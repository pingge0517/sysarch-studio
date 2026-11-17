package com.example.sysarch.identity;

import com.example.sysarch.access.ProjectReadAccess;
import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultEdge;
import graphql.relay.Edge;
import graphql.schema.DataFetcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.reactivestreams.Publisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Flux;

/** Uses resolved field arguments, so aliases, fragments, inline inputs and operationName cannot bypass the guard. */
public class GraphQLReadInstrumentation extends SimplePerformantInstrumentation {
    private final ProjectReadAccess reads;
    public GraphQLReadInstrumentation(ProjectReadAccess reads) { this.reads = reads; }

    static final class Scope {
        final Supplier<Optional<Principal>> identity;
        final Set<String> projects = ConcurrentHashMap.newKeySet();
        Scope(Supplier<Optional<Principal>> identity) { this.identity = identity; }
        Principal principal() { return this.identity.get().orElse(null); }
    }

    @Override public ExecutionInput instrumentExecutionInput(ExecutionInput input, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        Supplier<Optional<Principal>> identity = GraphQLReadIdentity.take();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            var principal = CurrentUser.principal(servlet.getRequest());
            identity = () -> principal;
        }
        Scope scope = new Scope(identity == null ? Optional::empty : identity);
        return input.transform(builder -> builder.graphQLContext(Map.of(Scope.class, scope)));
    }

    @Override public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> fetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        return environment -> {
            Scope scope = environment.getGraphQlContext().get(Scope.class);
            if (scope == null) throw new ProjectReadAccess.Denied();
            this.arguments(scope, environment.getArguments());
            if (environment.getSource() instanceof ProjectDTO project) this.authorize(scope, project.id());
            return this.result(fetcher.get(environment), scope);
        };
    }

    private void authorize(Scope scope, String project) {
        this.reads.require(scope.principal(), project);
        scope.projects.add(project);
    }

    private void arguments(Scope scope, Object value) {
        if (value instanceof Map<?, ?> fields) {
            fields.forEach((key, item) -> {
                if (item instanceof String id) {
                    if ("projectId".equals(key)) this.authorize(scope, this.reads.resolve(id));
                    if ("editingContextId".equals(key) || "semanticDataId".equals(key)) this.authorize(scope, this.reads.editingContext(id));
                } else this.arguments(scope, item);
            });
        } else if (value instanceof Iterable<?> items) items.forEach(item -> this.arguments(scope, item));
    }

    private Object result(Object value, Scope scope) {
        if (value instanceof CompletionStage<?> stage) return stage.thenApply(item -> this.result(item, scope));
        if (value instanceof Publisher<?> publisher) {
            return Flux.from(publisher).map(item -> {
                // Re-check membership and session validity on every emitted event, not only when subscribing.
                scope.projects.forEach(project -> this.reads.require(scope.principal(), project));
                return this.result(item, scope);
            });
        }
        if (value instanceof DataFetcherResult<?> result) return result.map(item -> this.result(item, scope));
        if (value instanceof Connection<?> connection) {
            boolean projects = connection.getEdges().stream().anyMatch(edge -> {
                Object node = edge.getNode();
                return (node instanceof DataFetcherResult<?> result ? result.getData() : node) instanceof ProjectDTO;
            });
            if (!projects) return value;
            List<Edge<Object>> edges = new ArrayList<>();
            for (Edge<?> edge : connection.getEdges()) {
                Object node = edge.getNode();
                Object unwrapped = node instanceof DataFetcherResult<?> result ? result.getData() : node;
                if (!(unwrapped instanceof ProjectDTO p) || this.reads.canRead(scope.principal(), p.id())) {
                    edges.add(new DefaultEdge<>(node, edge.getCursor()));
                }
            }
            return new DefaultConnection<>(edges, connection.getPageInfo());
        }
        return value;
    }
}
