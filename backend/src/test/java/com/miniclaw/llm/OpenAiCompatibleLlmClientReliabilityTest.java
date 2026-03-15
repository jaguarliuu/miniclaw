package com.miniclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OpenAiCompatibleLlmClientReliabilityTest {

    @Test
    void chatRetriesTransientFailuresAndEventuallySucceeds() throws Exception {
        try (ScriptedHttpServer server = ScriptedHttpServer.start(List.of(
                ScriptedResponse.json(503, """
                        {"error":{"message":"upstream overloaded"}}
                        """),
                ScriptedResponse.json(200, """
                        {
                          "choices":[{"message":{"role":"assistant","content":"retry ok"},"finish_reason":"stop"}],
                          "usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}
                        }
                        """)
        ))) {
            OpenAiCompatibleLlmClient client = createClient(server.port(), 2);

            LlmResponse response = client.chat(userRequest("hello"));

            assertEquals("retry ok", response.getContent());
            assertEquals(2, server.requestCount());
        }
    }

    @Test
    void chatDoesNotRetryAuthenticationFailures() throws Exception {
        try (ScriptedHttpServer server = ScriptedHttpServer.start(List.of(
                ScriptedResponse.json(401, """
                        {"error":{"message":"invalid api key"}}
                        """),
                ScriptedResponse.json(200, """
                        {
                          "choices":[{"message":{"role":"assistant","content":"should not happen"},"finish_reason":"stop"}]
                        }
                        """)
        ))) {
            OpenAiCompatibleLlmClient client = createClient(server.port(), 3);

            Throwable thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    LlmException.class,
                    () -> client.chat(userRequest("hello"))
            );

            LlmException exception = assertInstanceOf(LlmException.class, thrown);
            assertEquals(LlmErrorType.AUTHENTICATION, exception.getErrorType());
            assertFalse(exception.isRetryable());
            assertEquals(401, exception.getHttpStatus());
            assertEquals(1, server.requestCount());
        }
    }

    @Test
    void streamRetriesTransientFailuresAndEventuallyYieldsChunks() throws Exception {
        try (ScriptedHttpServer server = ScriptedHttpServer.start(List.of(
                ScriptedResponse.json(503, """
                        {"error":{"message":"please retry"}}
                        """),
                ScriptedResponse.sse("""
                        data: {"choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}

                        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                        data: [DONE]

                        """)
        ))) {
            OpenAiCompatibleLlmClient client = createClient(server.port(), 2);

            StepVerifier.create(client.stream(userRequest("hello")))
                    .assertNext(chunk -> {
                        assertEquals("Hi", chunk.getDelta());
                        assertFalse(chunk.isDone());
                    })
                    .assertNext(chunk -> {
                        assertEquals("stop", chunk.getFinishReason());
                        org.junit.jupiter.api.Assertions.assertTrue(chunk.isDone());
                    })
                    .assertNext(chunk -> org.junit.jupiter.api.Assertions.assertTrue(chunk.isDone()))
                    .verifyComplete();

            assertEquals(2, server.requestCount());
        }
    }

    private static OpenAiCompatibleLlmClient createClient(int port, int maxRetries) {
        LlmProperties properties = new LlmProperties();
        properties.setEndpoint("http://127.0.0.1:" + port);
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setTimeout(5);
        properties.setMaxRetries(maxRetries);
        properties.setRetryMinBackoffMillis(10L);
        properties.setRetryMaxBackoffMillis(20L);
        return new OpenAiCompatibleLlmClient(properties, new ObjectMapper());
    }

    private static LlmRequest userRequest(String prompt) {
        return LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user(prompt)))
                .build();
    }

    private record ScriptedResponse(int status, String contentType, String body, Map<String, String> headers) {
        static ScriptedResponse json(int status, String body) {
            return new ScriptedResponse(status, "application/json", body, Map.of());
        }

        static ScriptedResponse sse(String body) {
            return new ScriptedResponse(200, "text/event-stream", body, Map.of());
        }
    }

    private static final class ScriptedHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requestCount;

        private ScriptedHttpServer(HttpServer server, AtomicInteger requestCount) {
            this.server = server;
            this.requestCount = requestCount;
        }

        static ScriptedHttpServer start(List<ScriptedResponse> responses) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            Queue<ScriptedResponse> queue = new ArrayDeque<>(responses);
            AtomicInteger requestCount = new AtomicInteger();

            server.createContext("/v1/chat/completions", exchange -> {
                requestCount.incrementAndGet();
                ScriptedResponse response = queue.poll();
                if (response == null) {
                    response = ScriptedResponse.json(500, "{\"error\":{\"message\":\"no scripted response\"}}");
                }
                writeResponse(exchange, response);
            });
            server.start();

            return new ScriptedHttpServer(server, requestCount);
        }

        int port() {
            return server.getAddress().getPort();
        }

        int requestCount() {
            return requestCount.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void writeResponse(HttpExchange exchange, ScriptedResponse response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            for (Map.Entry<String, String> entry : response.headers().entrySet()) {
                exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
            }

            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }
}
