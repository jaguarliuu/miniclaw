package com.miniclaw.gateway.rpc.handler;

import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RpcHandler {

    List<String> supportedMethods();

    Mono<Object> handle(String connectionId, RpcRequestFrame request);
}
