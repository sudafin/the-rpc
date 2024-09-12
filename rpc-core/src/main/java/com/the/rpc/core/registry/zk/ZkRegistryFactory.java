package com.the.rpc.core.registry.zk;

import com.the.rpc.common.url.URL;
import com.the.rpc.core.registry.Registry;
import com.the.rpc.core.registry.RegistryFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * zk 注册中心工厂
 *
 */
public class ZkRegistryFactory implements RegistryFactory {

    private static final Map<URL, ZkRegistry> cache = new ConcurrentHashMap<>();

    @Override
    public Registry getRegistry(URL url) {
        if (cache.containsKey(url)) {
            return cache.get(url);
        }
        ZkRegistry zkRegistry = new ZkRegistry(url);
        cache.putIfAbsent(url, zkRegistry);
        return cache.get(url);
    }

}
