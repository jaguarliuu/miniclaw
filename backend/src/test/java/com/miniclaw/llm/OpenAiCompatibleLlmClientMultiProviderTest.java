package com.miniclaw.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProviderConfig;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleLlmClientMultiProviderTest {

    @Test
    void chatUsesDefaultProviderDeclaredByDefaultModel() throws Exception {
        try (RecordingHttpServer deepSeek = RecordingHttpServer.start("deepseek ok");
             RecordingHttpServer openAi = RecordingHttpServer.start("openai ok")) {

            LlmProperties properties = new LlmProperties();
            properties.setDefaultModel("deepseek:deepseek-chat");
            properties.setProviders(List.of(
                    LlmProviderConfig.builder()
                            .id("deepseek")
                            .endpoint("http://127.0.0.1:" + deepSeek.port())
                            .apiKey("deepseek-key")
                            .models(List.of("deepseek-chat"))
                            .build(),
                    LlmProviderConfig.builder()
                            .id("openai")
                            .endpoint("http://127.0.0.1:" + openAi.port())
                            .apiKey("openai-key")
                            .models(List.of("gpt-4o-mini"))
                            .build()
            ));

            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, new ObjectMapper());

            LlmResponse response = client.chat(LlmRequest.builder()
                    .messages(List.of(LlmRequest.Message.user("hello")))
                    .build());

            assertEquals("deepseek ok", response.getContent());
            assertEquals(1, deepSeek.requestCount());
            assertEquals(0, openAi.requestCount());
            assertEquals("deepseek-chat", deepSeek.lastModel());
        }
    }

    @Test
    void chatUsesRequestedProvidersFirstModelWhenModelIsOmitted() throws Exception {
        try (RecordingHttpServer deepSeek = RecordingHttpServer.start("deepseek ok");
             RecordingHttpServer openAi = RecordingHttpServer.start("openai ok")) {

            LlmProperties properties = new LlmProperties();
            properties.setDefaultModel("deepseek:deepseek-chat");
            properties.setProviders(List.of(
                    LlmProviderConfig.builder()
                            .id("deepseek")
                            .endpoint("http://127.0.0.1:" + deepSeek.port())
                            .apiKey("deepseek-key")
                            .models(List.of("deepseek-chat"))
                            .build(),
                    LlmProviderConfig.builder()
                            .id("openai")
                            .endpoint("http://127.0.0.1:" + openAi.port())
                            .apiKey("openai-key")
                            .models(List.of("gpt-4o-mini", "gpt-4.1"))
                            .build()
            ));

            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, new ObjectMapper());

            LlmResponse response = client.chat(LlmRequest.builder()
                    .providerId("openai")
                    .messages(List.of(LlmRequest.Message.user("hello")))
                    .build());

            assertEquals("openai ok", response.getContent());
            assertEquals(0, deepSeek.requestCount());
            assertEquals(1, openAi.requestCount());
            assertEquals("gpt-4o-mini", openAi.lastModel());
        }
    }

    private static final class RecordingHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicReference<String> lastBody = new AtomicReference<>();

        private RecordingHttpServer(HttpServer server) {
            this.server = server;
        }

        static RecordingHttpServer start(String content) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            RecordingHttpServer recordingServer = new RecordingHttpServer(server);
            server.createContext("/v1/chat/completions", exchange -> {
                recordingServer.requestCount.incrementAndGet();
                recordingServer.lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, """
                        {
                          "choices":[{"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}]
                        }
                        """.formatted(content));
            });
            server.start();
            return recordingServer;
        }

        int port() {
            return server.getAddress().getPort();
        }

        int requestCount() {
            return requestCount.get();
        }

        String lastModel() throws Exception {
            JsonNode root = new ObjectMapper().readTree(lastBody.get());
            return root.get("model").asText();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void writeJson(HttpExchange exchange, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
