package com.the.rpc.common.consts;

/**
 * Rpc异常常量
 */
public class RpcException extends RuntimeException {
    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

}
