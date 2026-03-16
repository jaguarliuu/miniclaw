package com.miniclaw.gateway.rpc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcCompletedFrame {

    @Builder.Default
    private String type = "completed";

    private String requestId;

    private String sessionId;

    private JsonNode payload;

    public static RpcCompletedFrame of(String requestId, String sessionId, JsonNode payload) {
        return RpcCompletedFrame.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .payload(payload)
                .build();
    }
}
