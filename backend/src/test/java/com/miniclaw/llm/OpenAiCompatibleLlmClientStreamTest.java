package com.miniclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

class OpenAiCompatibleLlmClientStreamTest {

    @Test
    void streamParsesContentFinishAndDoneEvents() throws Exception {
        HttpServer server = startSseServer("""
                data: {"choices":[{"delta":{"content":"Hel"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"lo"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """);

        try {
            OpenAiCompatibleLlmClient client = createClient(server);

            StepVerifier.create(client.stream(userRequest("Say hello")))
                    .assertNext(chunk -> assertChunk(chunk, "Hel", null, false))
                    .assertNext(chunk -> assertChunk(chunk, "lo", null, false))
                    .assertNext(chunk -> assertChunk(chunk, null, "stop", true))
                    .assertNext(chunk -> assertChunk(chunk, null, null, true))
                    .verifyComplete();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamIgnoresInvalidJsonEvents() throws Exception {
        HttpServer server = startSseServer("""
                data: {broken json}

                data: {"choices":[{"delta":{"content":"ok"},"finish_reason":null}]}

                data: [DONE]

                """);

        try {
            OpenAiCompatibleLlmClient client = createClient(server);

            StepVerifier.create(client.stream(userRequest("ping")))
                    .assertNext(chunk -> assertChunk(chunk, "ok", null, false))
                    .assertNext(chunk -> assertChunk(chunk, null, null, true))
                    .verifyComplete();
        } finally {
            server.stop(0);
        }
    }

    private static OpenAiCompatibleLlmClient createClient(HttpServer server) {
        LlmProperties properties = new LlmProperties();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setTimeout(5);
        return new OpenAiCompatibleLlmClient(properties, new ObjectMapper());
    }

    private static LlmRequest userRequest(String prompt) {
        return LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user(prompt)))
                .build();
    }

    private static HttpServer startSseServer(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        return server;
    }

    private static void assertChunk(LlmChunk chunk, String expectedDelta, String expectedFinishReason, boolean expectedDone) {
        org.junit.jupiter.api.Assertions.assertEquals(expectedDelta, chunk.getDelta());
        org.junit.jupiter.api.Assertions.assertEquals(expectedFinishReason, chunk.getFinishReason());
        org.junit.jupiter.api.Assertions.assertEquals(expectedDone, chunk.isDone());
    }
}
