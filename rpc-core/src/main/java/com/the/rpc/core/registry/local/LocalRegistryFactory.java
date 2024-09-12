package com.the.rpc.core.registry.local;

import com.the.rpc.common.url.URL;
import com.the.rpc.core.registry.Registry;
import com.the.rpc.core.registry.RegistryFactory;

/**
 */
public class LocalRegistryFactory implements RegistryFactory {

    @Override
    public Registry getRegistry(URL url) {
        return new LocalRegistry();
    }
}
