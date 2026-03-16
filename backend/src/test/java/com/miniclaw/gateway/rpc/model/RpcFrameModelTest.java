package com.miniclaw.gateway.rpc.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcFrameModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestFrameShouldCarryMethodSessionAndPayload() throws Exception {
        RpcRequestFrame frame = RpcRequestFrame.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .method("chat.send")
                .payload(objectMapper.valueToTree(Map.of("message", "hello gateway")))
                .build();

        String json = objectMapper.writeValueAsString(frame);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("request", root.get("type").asText());
        assertEquals("req-001", root.get("requestId").asText());
        assertEquals("session-001", root.get("sessionId").asText());
        assertEquals("chat.send", root.get("method").asText());
        assertEquals("hello gateway", root.get("payload").get("message").asText());

        RpcRequestFrame parsed = objectMapper.readValue(json, RpcRequestFrame.class);
        assertEquals("req-001", parsed.getRequestId());
        assertEquals("session-001", parsed.getSessionId());
        assertEquals("chat.send", parsed.getMethod());
        assertEquals("hello gateway", parsed.getPayload().get("message").asText());
    }

    @Test
    void eventFrameShouldCarryRequestSessionNameAndPayload() throws Exception {
        RpcEventFrame frame = RpcEventFrame.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .name("chat.delta")
                .payload(objectMapper.valueToTree(Map.of("delta", "hello")))
                .build();

        String json = objectMapper.writeValueAsString(frame);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("event", root.get("type").asText());
        assertEquals("req-001", root.get("requestId").asText());
        assertEquals("session-001", root.get("sessionId").asText());
        assertEquals("chat.delta", root.get("name").asText());
        assertEquals("hello", root.get("payload").get("delta").asText());

        RpcEventFrame parsed = objectMapper.readValue(json, RpcEventFrame.class);
        assertEquals("chat.delta", parsed.getName());
        assertEquals("hello", parsed.getPayload().get("delta").asText());
    }

    @Test
    void completedFrameShouldCarryRequestSessionAndPayload() throws Exception {
        RpcCompletedFrame frame = RpcCompletedFrame.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .payload(objectMapper.valueToTree(Map.of("status", "finished")))
                .build();

        String json = objectMapper.writeValueAsString(frame);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("completed", root.get("type").asText());
        assertEquals("req-001", root.get("requestId").asText());
        assertEquals("session-001", root.get("sessionId").asText());
        assertEquals("finished", root.get("payload").get("status").asText());

        RpcCompletedFrame parsed = objectMapper.readValue(json, RpcCompletedFrame.class);
        assertEquals("finished", parsed.getPayload().get("status").asText());
    }

    @Test
    void errorFrameShouldCarryRequestSessionCodeAndMessage() throws Exception {
        RpcErrorFrame frame = RpcErrorFrame.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .error(new RpcErrorPayload("METHOD_NOT_FOUND", "Unknown method: chat.run"))
                .build();

        String json = objectMapper.writeValueAsString(frame);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("error", root.get("type").asText());
        assertEquals("req-001", root.get("requestId").asText());
        assertEquals("session-001", root.get("sessionId").asText());
        assertEquals("METHOD_NOT_FOUND", root.get("error").get("code").asText());
        assertEquals("Unknown method: chat.run", root.get("error").get("message").asText());

        RpcErrorFrame parsed = objectMapper.readValue(json, RpcErrorFrame.class);
        assertEquals("METHOD_NOT_FOUND", parsed.getError().getCode());
        assertEquals("Unknown method: chat.run", parsed.getError().getMessage());
    }
}
