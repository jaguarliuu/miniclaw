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
public class RpcEventFrame {

    @Builder.Default
    private String type = "event";

    private String requestId;

    private String sessionId;

    private String name;

    private JsonNode payload;

    public static RpcEventFrame of(String requestId, String sessionId, String name, JsonNode payload) {
        return RpcEventFrame.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .name(name)
                .payload(payload)
                .build();
    }
}
