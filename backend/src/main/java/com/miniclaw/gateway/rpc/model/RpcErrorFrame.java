package com.miniclaw.gateway.rpc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcErrorFrame {

    @Builder.Default
    private String type = "error";

    private String requestId;

    private String sessionId;

    private RpcErrorPayload error;

    public static RpcErrorFrame of(String requestId, String sessionId, String code, String message) {
        return RpcErrorFrame.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .error(new RpcErrorPayload(code, message))
                .build();
    }
}
