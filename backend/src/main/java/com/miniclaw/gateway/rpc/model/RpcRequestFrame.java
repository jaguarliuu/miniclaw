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
public class RpcRequestFrame {

    @Builder.Default
    private String type = "request";

    private String requestId;

    private String sessionId;

    private String method;

    private JsonNode payload;
}
