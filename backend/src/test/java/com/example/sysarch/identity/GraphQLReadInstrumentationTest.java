package com.example.sysarch.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.sysarch.access.ProjectReadFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.DataFetcherResult;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class GraphQLReadInstrumentationTest {
    private ProjectReadFixture f;
    private GraphQL graphQL;
    private final AtomicInteger secrets = new AtomicInteger();
    private final Sinks.Many<Map<String,String>> events = Sinks.many().unicast().onBackpressureBuffer();

    @BeforeEach void setup() {
        f=new ProjectReadFixture();
        String sdl="""
                type Query { viewer: Viewer! }
                type Viewer { project(projectId: ID!): Project projects: Projects! editingContext(editingContextId: ID!): Model }
                type Project { id: ID! name: String! }
                type Projects { edges: [ProjectEdge!]! }
                type ProjectEdge { node: Project! }
                type Model { secret: String }
                input ModelInput { editingContextId: ID! }
                type Mutation { readModel(input: ModelInput!): Model }
                type Subscription { modelEvent(input: ModelInput!): Model }
                """;
        List<graphql.relay.Edge<DataFetcherResult<ProjectDTO>>> edges=List.of(
                new DefaultEdge<>(DataFetcherResult.<ProjectDTO>newResult().data(new ProjectDTO("p-a","Alpha model",List.of())).build(),new DefaultConnectionCursor("a")),
                new DefaultEdge<>(DataFetcherResult.<ProjectDTO>newResult().data(new ProjectDTO("p-b","Beta secret",List.of())).build(),new DefaultConnectionCursor("b")));
        var wiring=RuntimeWiring.newRuntimeWiring()
                .type("Query",t->t.dataFetcher("viewer",e->Map.of()))
                .type("Viewer",t->t.dataFetcher("project",e->new ProjectDTO(e.getArgument("projectId"),"selected project",List.of()))
                        .dataFetcher("projects",e->new DefaultConnection<>(edges,new DefaultPageInfo(null,null,false,false)))
                        .dataFetcher("editingContext",e->{secrets.incrementAndGet();return Map.of("secret","private model");}))
                .type("Mutation",t->t.dataFetcher("readModel",e->{secrets.incrementAndGet();return Map.of("secret","private model");}))
                .type("Subscription",t->t.dataFetcher("modelEvent",e->events.asFlux()))
                .build();
        var schema=new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(sdl),wiring);
        graphQL=GraphQL.newGraphQL(schema).instrumentation(new GraphQLReadInstrumentation(f.reads)).build();
    }

    @AfterEach void cleanup(){GraphQLReadIdentity.clear();RequestContextHolder.resetRequestAttributes();}

    private ExecutionResult execute(String query) {
        GraphQLReadIdentity.prime(()->Optional.of(f.alice));
        return graphQL.execute(query);
    }

    @Test void projectPaginationFiltersDataFetcherResultNodesAsUsedBySirius() {
        var result=execute("{ viewer { projects { edges { node { id name } } } } }");
        assertTrue(result.getErrors().isEmpty(),result.getErrors().toString());
        String output=result.toSpecification().toString();
        assertTrue(output.contains("Alpha model"));
        assertFalse(output.contains("Beta secret"));
        assertFalse(output.contains("p-b"));
    }

    @Test void aliasesAndFragmentsCannotHideAnotherProjectsId() {
        var result=execute("query { v: viewer { ...target } } fragment target on Viewer { hidden: project(projectId: \"p-b\") { id name } }");
        assertFalse(result.getErrors().isEmpty());
        assertFalse(result.toSpecification().toString().contains("selected project"));
    }

    @Test void editingContextIdsAreCheckedBeforeAnyModelLookup() {
        var result=execute("{ viewer { editingContext(editingContextId: \"ec-b\") { secret } } }");
        assertFalse(result.getErrors().isEmpty());
        assertEquals(0,secrets.get());
        var allowed=execute("{ viewer { editingContext(editingContextId: \"ec-a\") { secret } } }");
        assertTrue(allowed.getErrors().isEmpty());
        assertEquals(1,secrets.get());
    }

    @Test void resolvedVariablesAndInlineInputObjectsUseTheSameGuard() {
        var inline=execute("mutation { readModel(input: {editingContextId: \"ec-b\"}) { secret } }");
        assertFalse(inline.getErrors().isEmpty());
        GraphQLReadIdentity.prime(()->Optional.of(f.alice));
        var variables=graphQL.execute(ExecutionInput.newExecutionInput().query("mutation Read($payload: ModelInput!) { readModel(input:$payload) {secret} }")
                .variables(Map.of("payload",Map.of("editingContextId","ec-b"))).operationName("Read").build());
        assertFalse(variables.getErrors().isEmpty());
        assertEquals(0,secrets.get());
    }

    @Test void websocketSubscriptionsReceiveVerifiedHandshakeIdentityAndRejectOtherProjects() throws Exception {
        var identities=mock(Identities.class);
        var session=mock(WebSocketSession.class);
        var headers=new HttpHeaders();
        headers.add("Cookie","sysarch_session=signed-fixture");
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(identities.resolveHeaders(headers)).thenReturn(Optional.of(f.alice));
        var listener=new GraphQLReadConfiguration().projectReadWebSocketListener(identities,new ObjectMapper());
        listener.handleTextMessage(session,new TextMessage("{\"type\":\"start\",\"payload\":{\"query\":\"subscription\"}}"));
        var result=graphQL.execute("subscription { modelEvent(input:{editingContextId:\"ec-b\"}){secret} }");
        assertFalse(result.getErrors().isEmpty());
        assertNull(GraphQLReadIdentity.take(),"the thread-local identity is consumed rather than retained on the worker");
        verify(identities,atLeastOnce()).resolveHeaders(headers);
    }

    @Test void anExpiredSubscriptionIdentityCannotReceiveFurtherModelEvents() {
        var identity=new AtomicReference<>(f.alice);
        GraphQLReadIdentity.prime(()->Optional.ofNullable(identity.get()));
        var result=graphQL.execute("subscription { modelEvent(input:{editingContextId:\"ec-a\"}){secret} }");
        assertTrue(result.getErrors().isEmpty(),result.getErrors().toString());
        Publisher<ExecutionResult> publisher=result.getData();
        List<ExecutionResult> received=new ArrayList<>();
        var failure=new AtomicReference<Throwable>();
        var subscription=Flux.from(publisher).subscribe(received::add,failure::set);
        events.tryEmitNext(Map.of("secret","first event"));
        assertEquals(1,received.size());
        identity.set(null);
        events.tryEmitNext(Map.of("secret","must not leak"));
        assertEquals(1,received.size());
        assertNotNull(failure.get());
        subscription.dispose();
    }

    @Test void anHttpRequestNeverInheritsAStaleWebsocketIdentity() {
        var request=new MockHttpServletRequest();
        CurrentUser.attach(request,()->Optional.of(f.alice));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        GraphQLReadIdentity.prime(()->Optional.of(f.admin));
        var result=graphQL.execute("{ viewer { project(projectId:\"p-b\"){id name} } }");
        assertFalse(result.getErrors().isEmpty());
    }

    @Test void removingAnExplicitGlobalReadGrantStopsAnExistingSubscription() {
        var auditor=Principal.dev("audit",List.of("global:auditor"));
        when(f.roles.list()).thenReturn(List.of(new com.example.sysarch.access.RoleMappings.Row(
                1,"alpha","global:auditor",com.example.sysarch.access.ToolRole.OBSERVER,"root",null)));
        GraphQLReadIdentity.prime(()->Optional.of(auditor));
        var result=graphQL.execute("subscription { modelEvent(input:{editingContextId:\"ec-a\"}){secret} }");
        assertTrue(result.getErrors().isEmpty());
        Publisher<ExecutionResult> publisher=result.getData();
        List<ExecutionResult> received=new ArrayList<>();
        var failure=new AtomicReference<Throwable>();
        var subscription=Flux.from(publisher).subscribe(received::add,failure::set);
        events.tryEmitNext(Map.of("secret","allowed"));
        assertEquals(1,received.size());
        when(f.roles.list()).thenReturn(List.of());
        events.tryEmitNext(Map.of("secret","revoked"));
        assertEquals(1,received.size());
        assertNotNull(failure.get());
        subscription.dispose();
    }

    @Test void springDecoratesTheNativeGraphQLBeanWithoutReplacingItsSchema() {
        GraphQL nativeGraphQL=graphQL.transform(builder->builder.instrumentation(new graphql.execution.instrumentation.SimplePerformantInstrumentation()));
        new ApplicationContextRunner().withUserConfiguration(GraphQLReadConfiguration.class)
                .withBean(com.example.sysarch.access.ProjectReadAccess.class,()->f.reads)
                .withBean(Identities.class,()->mock(Identities.class))
                .withBean(ObjectMapper.class,ObjectMapper::new)
                .withBean(GraphQL.class,()->nativeGraphQL)
                .run(context->{
                    assertNull(context.getStartupFailure());
                    GraphQLReadIdentity.prime(()->Optional.of(f.alice));
                    var result=context.getBean(GraphQL.class).execute("{viewer{projects{edges{node{id name}}}}}");
                    assertTrue(result.getErrors().isEmpty(),result.getErrors().toString());
                    assertFalse(result.toSpecification().toString().contains("Beta secret"));
                    assertTrue(result.toSpecification().toString().contains("Alpha model"));
                });
    }
}
