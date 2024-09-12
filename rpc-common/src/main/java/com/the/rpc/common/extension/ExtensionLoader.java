package com.the.rpc.common.extension;

import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>扩展类加载器。</p>
 * <p>扩展类的配置写到 {@code META-INF/extensions/ccx-rpc} 目录下，文件名为接口全名。</p>
 * <p>文件格式为：扩展名=扩展类全名。例如：{@code zk=com.ccx.core.registry.ZkRegistry}</p>
 * <p>获取扩展类实例的代码示例如下：</p>
 * <pre>{@code
 *     ExtensionLoader loader = ExtensionLoader.getExtensionLoader(Registry.class)
 *     Registry registry = loader.getExtension("zk");
 * }</pre>
 *
 */
public class ExtensionLoader<T> {
   //以下都以注册中心举例, com.ccx.rpc.core.registry.RegistryFactory文件里的zk=com.ccx.rpc.core.registry.zk.ZkRegistryFactory
    /**
     * 扩展类实例缓存 {name: 扩展类实例}  {zk : ZkRegistryFactory实例对象} 这里与extensionClassesCache 做区分, 后者是class
     *
     */
    private final Map<String, T> extensionsCache = new ConcurrentHashMap<>();

    /**
     * 扩展加载器实例缓存 {类型：加载器实例}, {RegistryFactory: ExtensionLoader<RegistryFactory>}
     */
    private static final Map<Class<?>, ExtensionLoader<?>> extensionLoaderCache = new ConcurrentHashMap<>();

    /**
     * 扩展类配置列表缓存 {type: {name, 扩展类}} {} {zk : RegistryFactory.class}
     */
    private final Holder<Map<String, Class<?>>> extensionClassesCache = new Holder<>();

    /**
     * 创建扩展实例类的锁缓存 {name: synchronized 持有的锁} {zk , 对象锁}
     */
    private final Map<String, Object> createExtensionLockMap = new ConcurrentHashMap<>();

    /**
     * 扩展类加载器的类型  {RegistryFactory.class}
     */
    private final Class<T> type;

    /**
     * 扩展类存放的目录地址
     */
    private static final String EXTENSION_PATH = "META-INF/ccx-rpc/";

    /**
     * 默认扩展名缓存
     */
    private final String defaultNameCache;

    /**
     * 构造函数
     *
     * @param type 扩展类加载器的类型 这里举RegistryFactory
     */
    private ExtensionLoader(Class<T> type) {
        this.type = type;
        SPI annotation = type.getAnnotation(SPI.class);
        //获取在对应的SPI的value设置自定义限定路径, 如果没有设置返回default
        defaultNameCache = annotation.value();
    }

    /**
     * 获取对应类型的扩展加载器实例
     *
     * @param type 扩展类加载器的类型 这里举RegistryFactory接口
     * @return 扩展类加载器实例
     */
    public static <S> ExtensionLoader<S> getLoader(Class<S> type) {
        // 扩展加载器的要扩展类型必须是接口, 也就是说只要接口才能当扩展加载器,其实现类就是扩展类
        if (!type.isInterface()) {
            throw new IllegalStateException(type.getName() + " is not interface");
        }
        //查看该接口是否有SPI注解
        SPI annotation = type.getAnnotation(SPI.class);
        if (annotation == null) {
            throw new IllegalStateException(type.getName() + " has not @SPI annotation.");
        }
        //查看本地缓存是否已经有对应的扩展加载器实例, 如果有就返回, 反之就新创建一个对应的扩展加载器实例
        ExtensionLoader<?> extensionLoader = extensionLoaderCache.get(type);
        if (extensionLoader != null) {
            //noinspection unchecked
            return (ExtensionLoader<S>) extensionLoader;
        }
        //创建扩展加载器类实例
        extensionLoader = new ExtensionLoader<>(type);
        //放入缓存当中 {RegistryFactory.class,ExtensionLoader<RegistryFactory>}
        extensionLoaderCache.putIfAbsent(type, extensionLoader);
        //noinspection unchecked
        return (ExtensionLoader<S>) extensionLoader;
    }

    /**
     * 获取默认的扩展类实例，会自动加载 @SPI 注解中的 value 指定的类实例
     *
     * @return 返回该类的注解 @SPI.value 指定的类实例
     */
    public T getDefaultExtension() {
        return getExtension(defaultNameCache);
    }

    /**
     * 根据名字获取扩展类实例(单例)
     *
     * @param name 扩展类在配置文件中配置的名字. 如果名字是空的或者空白的，则返回默认扩展
     * @return 单例扩展类实例，如果找不到，则抛出异常
     */
    public T getExtension(String name) {
        if (StrUtil.isBlank(name)) {
            return getDefaultExtension();
        }
        // 从缓存中获取扩展类单例(扩展加载器的实现类, 因为扩展加载器是由接口实现的所以它的实现类就是扩展类)
        T extension = extensionsCache.get(name);
        if (extension == null) {
            //没有就需要创建扩展类,因为防止多个线程同时获取到单例对象,需要设置对应的name的资源锁
            Object lock = createExtensionLockMap.computeIfAbsent(name, k -> new Object());
            synchronized (lock) {
                //再次获取扩展类,防止在之前已经有线程创建了该实现类
                extension = extensionsCache.get(name);
                if (extension == null) {
                    //正式创建扩展类 ZkRegistryFactory
                    extension = createExtension(name);
                    //放入本地缓存中 {zk, ZkRegistryFactory}
                    extensionsCache.put(name, extension);
                }
            }
        }
        return extension;
    }

    /**
     * 获取自适应扩展类
     *
     * @return 动态代理自适应类
     */
    public T getAdaptiveExtension() {
        //将RegistryFactory.class传入到AdaptiveInvocationHandler动态代理处理器中
        InvocationHandler handler = new AdaptiveInvocationHandler<>(type);
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(
                ExtensionLoader.class.getClassLoader(),
                new Class<?>[]{type},
                handler
        );
    }

    /**
     * 创建对应名字的扩展类实例
     *
     * @param name 扩展名
     * @return 扩展类实例
     */
    private T createExtension(String name) {
        // 获取当前类型所有扩展类, 因为除了ZkRegistryFactory可能还有其他实现类
        Map<String, Class<?>> extensionClasses = getAllExtensionClasses();
        // 再根据名字找到对应的扩展类
        Class<?> clazz = extensionClasses.get(name);
        if (clazz == null) {
            throw new IllegalStateException("Extension not found. name=" + name + ", type=" + type.getName());
        }
        try {
            //noinspection unchecked
            return (T) clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Extension not found. name=" + name + ", type=" + type.getName() + ". " + e.toString());
        }
    }

    /**
     * 获取当前类型{@link #type}的所有扩展类
     *
     * @return {name: clazz}
     */
    private Map<String, Class<?>> getAllExtensionClasses() {
        //先获取全部的扩展类
        Map<String, Class<?>> extensionClasses = extensionClassesCache.get();
        //如果不为空说明已经有对应的全部扩展实现类,直接返回
        if (extensionClasses != null) {
            return extensionClasses;
        }
        //如果没有说明需要获取, 还是防止并发问题
        synchronized (extensionClassesCache) {
            extensionClasses = extensionClassesCache.get();
            if (extensionClasses == null) {
                //正式获取到全部的扩展实现类
                extensionClasses = loadClassesFromResources();
                //获取到全部的对应的扩展实现类后放入缓存中 , [{zk: ZkRegistryFactory.class}, {http: httpRegistryFactory.class}]
                extensionClassesCache.set(extensionClasses);
            }
        }
        //返回
        return extensionClasses;
    }

    /**
     * 从资源文件中加载所有扩展类
     *
     * @return {name: 扩展类}
     */
    private Map<String, Class<?>> loadClassesFromResources() {
        //新创一个所有扩展类集合用于设置
        Map<String, Class<?>> extensionClasses = new ConcurrentHashMap<>();
        // 扩展配置文件名 META-INF/ccx-rpc/type.getName(会获取RegistryFactory.class的全限定名com.ccx.rpc.core.registry.RegistryFactory)
        String fileName = EXTENSION_PATH + type.getName();
        // 拿到资源文件夹resource里的META-INF/ccx-rpc
        ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
        try {
            //根据资源文件夹拿到对应的文件,读取文件的内容
            Enumeration<URL> resources = classLoader.getResources(fileName);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    // 开始读文件
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        //拿到一行后就解析行,如 zk=com.ccx.rpc.core.registry.zk.ZkRegistryFactory, 需要将集合传过去
                        parseLine(line, extensionClasses);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Parse file fail. " + e.toString());
        }
        //当设置完后将集合返回回去
        return extensionClasses;
    }

    /**
     * 解析行，并且把解析到的类，放到 extensionClasses 中
     *
     * @param line             行
     * @param extensionClasses 扩展类列表
     * @throws ClassNotFoundException 找不到类
     */
    private void parseLine(String line, Map<String, Class<?>> extensionClasses) throws ClassNotFoundException {
        line = line.trim();
        // 忽略#号开头的注释
        if (line.startsWith("#")) {
            return;
        }
        //以等号做分割,以[0] = name [1] = 实现类的全限定名
        String[] kv = line.split("=");
        //当出现这三种情况说明设置有误
        if (kv.length != 2 || kv[0].isEmpty() || kv[1].isEmpty()) {
            throw new IllegalStateException("Extension file parsing error. Invalid format!");
        }
        //如果集合里面已经有它就不需要再次设置, 我们只允许一个name对应的它实现类
        if (extensionClasses.containsKey(kv[0])) {
            throw new IllegalStateException(kv[0] + " is already exists!");
        }
        //如果没有就要根据全限定名获取类信息,如拿到ZkRegistryFactory.class
        Class<?> clazz = ExtensionLoader.class.getClassLoader().loadClass(kv[1]);
        //将对应name的value设置为对应的类信息{zk,ZkRegistryFactory.class},然后返回
        extensionClasses.put(kv[0], clazz);
    }
}
