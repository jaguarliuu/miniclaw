package com.miniclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmResponseParserTest {

    @Test
    void parsesChatResponseWithUsageAndToolCalls() {
        LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

        LlmResponse response = parser.parseChat("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Tool response",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "get_weather",
                              "arguments": "{\\"city\\":\\"Shanghai\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 8,
                    "total_tokens": 20
                  }
                }
                """);

        assertEquals("Tool response", response.getContent());
        assertEquals("tool_calls", response.getFinishReason());
        assertEquals(1, response.getToolCalls().size());
        assertEquals(20, response.getUsage().getTotalTokens());
    }

    @Test
    void parsesSseDeltaChunk() {
        LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

        Optional<LlmChunk> chunk = parser.parseSseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}"
        );

        assertTrue(chunk.isPresent());
        assertEquals("hello", chunk.get().getDelta());
        assertFalse(chunk.get().isDone());
    }

    @Test
    void parsesDoneMarker() {
        LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

        Optional<LlmChunk> chunk = parser.parseSseLine("data: [DONE]");

        assertTrue(chunk.isPresent());
        assertTrue(chunk.get().isDone());
    }

    @Test
    void ignoresMalformedSseJson() {
        LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

        Optional<LlmChunk> chunk = parser.parseSseLine("data: {broken json}");

        assertTrue(chunk.isEmpty());
    }
}
