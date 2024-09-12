package com.the.rpc.common.extension;

import com.the.rpc.common.consts.URLKeyConst;
import com.the.rpc.common.url.URL;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 */
public class AdaptiveInvocationHandler<T> implements InvocationHandler {

    private final Class<T> clazz;

    public AdaptiveInvocationHandler(Class<T> tClass) {
        clazz = tClass;
    }

    //这里要增强的是RegistryFactory接口中的getRegistry(URL url)方法它被Adaptive注解
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //如果方法参数为0说明没有传入要zookeeper的注册url, 直接执行代理对象的原方法
        if (args.length == 0) {
            return method.invoke(proxy, args);
        }
        // 如果有参数就要看这参数是否是URL对象
        URL url = null;
        for (Object arg : args) {
            if (arg instanceof URL) {
                url = (URL) arg;
                break;
            }
        }
        //没有就还是要执行原方法
        if (url == null) {
            return method.invoke(proxy, args);
        }
        //查看该方法有被Adaptive注解吗, 没有还是执行原方法
        Adaptive adaptive = method.getAnnotation(Adaptive.class);
        if (adaptive == null) {
            return method.invoke(proxy, args);
        }
        //获取注解值,查看是不是属于协议的方法
        String extendNameKey = adaptive.value();
        //定义协议名
        String extendName;
        //如果该方法是协议就将url里参数协议名设置好, 比如在url中我们设置协议为protocol : zk, 那么它就会注解判断是不是protocol方法, 如果是就拿到URL中的protocol key ,然后拿到具体协议名
        if (URLKeyConst.PROTOCOL.equals(extendNameKey)) {
            extendName = url.getProtocol();
        } else {
            //如果定义参数没有协议protocol,那么就查看是否有扩展名是否有协议名, 没有就将该方法的全限定名作为协议名
            extendName = url.getParam(extendNameKey, method.getDeclaringClass() + "." + method.getName());
        }
        //获取该clazz的扩展加载器 RegistryFactory的扩展加载器
        ExtensionLoader<T> extensionLoader = ExtensionLoader.getLoader(clazz);
        //根据协议名也就是name= zk ,扩展假期器中的name为zk的扩展类
        T extension = extensionLoader.getExtension(extendName);
        //最后将执行扩展类(实现方法调用)
        return method.invoke(extension, args);
    }
}
