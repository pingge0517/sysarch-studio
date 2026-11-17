package com.example.sysarch.identity;

import com.example.sysarch.access.ProjectReadAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import java.util.List;
import org.eclipse.sirius.components.graphql.ws.api.IGraphQLWebSocketHandlerListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Configuration
public class GraphQLReadConfiguration {
    @Bean static BeanPostProcessor projectGraphQLInstrumentation(ObjectProvider<ProjectReadAccess> reads) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof GraphQL graphQL) {
                    return graphQL.transform(builder -> builder.instrumentation(new ChainedInstrumentation(List.of(
                            graphQL.getInstrumentation(), new GraphQLReadInstrumentation(reads.getObject())))));
                }
                return bean;
            }
        };
    }

    @Bean @Primary IGraphQLWebSocketHandlerListener projectReadWebSocketListener(Identities identities, ObjectMapper json) {
        return new IGraphQLWebSocketHandlerListener() {
            @Override public void handleTextMessage(WebSocketSession session, TextMessage message) {
                GraphQLReadIdentity.clear();
                try {
                    var node = json.readTree(message.getPayload());
                    if (node != null && "start".equals(node.path("type").asText()) && node.path("payload").path("query").isTextual()) {
                        GraphQLReadIdentity.prime(() -> identities.resolveHeaders(session.getHandshakeHeaders()));
                    }
                } catch (java.io.IOException e) { /* The native protocol handler rejects malformed messages. */ }
            }
            @Override public void afterConnectionEstablished(WebSocketSession session) { GraphQLReadIdentity.clear(); }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { GraphQLReadIdentity.clear(); }
        };
    }
}
