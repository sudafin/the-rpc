package com.the.rpc.core.registry.zk;

import com.the.rpc.common.consts.URLKeyConst;
import com.the.rpc.common.url.URL;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * curator zk 客户端
 *

 */
@Slf4j
public class CuratorZkClient {
    /**
     * 默认连接超时毫秒数,通常在客户端(RPC中是服务端和客户端)首次建立连接到zookeeper起作用,超过TCP最大等待时间就重试或抛出异常
     */
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 5_000;
    /**
     * 默认 session 超时毫秒数value,通常是客户端(PRC中是服务端和服务端)建立连接后, 在默认时间里没有定时发送ping给zookeeper, zookeeper会认为该客户端断开连接
     */
    private static final int DEFAULT_SESSION_TIMEOUT_MS = 60_000;
    /**
     * session 超时时间key
     */
    private static final String SESSION_TIMEOUT_KEY = "zk.sessionTimeoutMs";
    /**
     * 连接重试次数
     */
    private static final int RETRY_TIMES = 3;
    /**
     * 连接重试睡眠毫秒数
     */
    private static final int RETRY_SLEEP_MS = 1000;
    /**
     * 根目录,也就是父节点
     */
    private static final String ROOT_PATH = "/the-rpc";
    /**
     * zk 客户端
     */
    private final CuratorFramework client;
    /**
     * 监听器 {path: 监听器},用来监听RPC中服务端是否下线
     */
    private static final Map<String, CuratorCache> LISTENER_MAP = new ConcurrentHashMap<>();

    /**
     * 构造方法, 用于创建zookeeper客户端
     * @param url 自定义url地址类, 里面有一般url的参数格式用key:value存储, 客户端会将zookeeper的url传进来
     */
    public CuratorZkClient(URL url) {
        //获取连接最大超时时间值, 如果客户端设置zookeeper的url时无自定义时间值则使用默认设置的时间值
        int timeout = url.getIntParam(URLKeyConst.TIMEOUT, DEFAULT_CONNECTION_TIMEOUT_MS);
        //获取session超时时间值,
        int sessionTimeout = url.getIntParam(SESSION_TIMEOUT_KEY, DEFAULT_SESSION_TIMEOUT_MS);
        //创建工厂类构造一个zookeeper客户端,下面是输入信息
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(url.getAddress()) //连接zookeeper的网址
                .retryPolicy(new RetryNTimes(RETRY_TIMES, RETRY_SLEEP_MS)) // 重试策略
                .connectionTimeoutMs(timeout) // 连接时间
                .sessionTimeoutMs(sessionTimeout); // session时间
        //digest用于保护未提供完全登录信息的认证方式, 只要url没有传用户或密码就要设置digest,让RPC的服务端重新登录
        if (StringUtils.isNotEmpty(url.getUsername()) || StringUtils.isNotEmpty(url.getPassword())) {
            //设置为空字符串防止出现null报错
            String authority = StringUtils.defaultIfEmpty(url.getUsername(), "")
                    + ":" + StringUtils.defaultIfEmpty(url.getPassword(), "");
            //设置认证digest模式,账号密码转为二进制传输
            builder.authorization("digest", authority.getBytes());
        }
        //数据设置完创建zookeeper客户端并启动
        client = builder.build();
        client.start();
        try {
            //阻塞当前线程,等待可兑换
            client.blockUntilConnected(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Time out waiting to connect to zookeeper! Please check the zookeeper config.");
        }
    }

    /**
     * 创建节点
     *
     * @param path       路径，如果没有加上根目录，会自动加上根目录
     * @param createMode 节点模式,如持久性, 持久性顺序性, 临时节点, 临时顺序性
     */
    public void createNode(String path, CreateMode createMode) {
        try {
            client.create().creatingParentsIfNeeded().withMode(createMode).forPath(buildPath(path));
        } catch (KeeperException.NodeExistsException e) {
            log.warn("ZNode " + path + " already exists.");
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 创建永久节点
     *
     * @param path 路径，如果没有加上根目录，会自动加上根目录
     */
    public void createPersistentNode(String path) {
        createNode(path, CreateMode.PERSISTENT);
    }

    /**
     * 创建临时节点
     *
     * @param path 路径，如果没有加上根目录，会自动加上根目录
     */
    public void createEphemeralNode(String path) {
        createNode(path, CreateMode.EPHEMERAL);
    }

    /**
     * 删除节点
     *
     * @param path 路径，如果没有加上根目录，会自动加上根目录
     */
    public void removeNode(String path) {
        try {
            client.delete().forPath(buildPath(path));
        } catch (KeeperException.NoNodeException ignored) {
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * 获取路径下的所有子节点
     *
     * @param path 要搜索的路径
     * @return 节点不存在返回空列表
     */
    public List<String> getChildren(String path) {
        try {
            return client.getChildren().forPath(buildPath(path));
        } catch (KeeperException.NoNodeException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 添加监听者
     *
     * @param path     相对路径
     * @param listener 监听者
     */
    public void addListener(String path, CuratorCacheListener listener) {
        //对原来的path创建一个完整的路径(节点)
        String fullPath = buildPath(path);
        //查看监听Map是否有存在这个key路径(节点),如果包含就退出,不需要重复添加
        if (LISTENER_MAP.containsKey(fullPath)) {
            return;
        }
        //创建CuratorCache类用于缓存和监听 ZooKeeper 路径上的数据变化,它可以监听节点
        CuratorCache curatorCache = CuratorCache.build(client, fullPath);
        //将传进去来的永久监听器添加, 只要不移除将永远监听这个节点的变化
        curatorCache.listenable().addListener(listener);
        //将路径作为key和对应的curatorCache作为value,以便后续使用curatorCache, 如关闭监听器,处理监听的事件
        LISTENER_MAP.put(fullPath, curatorCache);
        //开启监听
        curatorCache.start();
    }

    /**
     * 构建完整的路径，用于存 zk
     *
     * @param path 相对或者路径
     * @return 如果路径不包含根目录，加上根目录
     */
    private String buildPath(String path) {
        if (path.startsWith(ROOT_PATH)) {
            return path;
        }
        if (path.startsWith("/")) {
            return ROOT_PATH + path;
        }
        return ROOT_PATH + "/" + path;
    }
}
