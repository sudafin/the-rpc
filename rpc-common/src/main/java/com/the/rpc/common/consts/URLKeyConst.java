package com.the.rpc.common.consts;

/**
 * URL 上面的参数 Key 常量
 */
public interface URLKeyConst {

    //客户端一开始连接到Zookeeper时连接时间的字段常量
    String TIMEOUT = "timeout";

    String DEFAULT_PREFIX = "default.";

    String DEFAULT = "default";

    //具体指定服务名
    String INTERFACE = "interface";

    //协议名: zk ,http等
    String PROTOCOL = "protocol";

    //版本号
    String VERSION = "version";

    String ANY_HOST = "anyHost";

    String THE_RPC_PROTOCOL = "the-rpc";
}
