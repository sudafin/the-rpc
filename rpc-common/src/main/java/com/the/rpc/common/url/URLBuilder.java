package com.the.rpc.common.url;

import cn.hutool.core.map.MapUtil;
import com.the.rpc.common.consts.URLKeyConst;

import java.util.Map;

/**
 */
public class URLBuilder {

    /**
     * 获取url上相关服务的参数
     *
     * @return 参数
     */
    public static Map<String, String> getServiceParam(String interfaceName, String rpcVersion) {
        return MapUtil.<String, String>builder()
                .put(URLKeyConst.INTERFACE, interfaceName)
                .put(URLKeyConst.VERSION, rpcVersion).build();
    }

    /**
     * 获取url上相关服务的参数
     *
     * @return 参数
     */
    public static Map<String, String> getServiceParam(Class<?> interfaceClass, String rpcVersion) {
        return getServiceParam(interfaceClass.getCanonicalName(), rpcVersion);
    }
}
