package com.miniclaw.gateway.rpc.handler;

import com.miniclaw.gateway.rpc.model.RpcRequestFrame;

import java.util.List;

public interface RpcHandler {

    List<String> supportedMethods();

    Object handle(String connectionId, RpcRequestFrame request);
}
