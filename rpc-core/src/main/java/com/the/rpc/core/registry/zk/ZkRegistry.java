package com.the.rpc.core.registry.zk;

import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.net.URLEncoder;
import com.the.rpc.common.consts.RegistryConst;
import com.the.rpc.common.url.URL;
import com.the.rpc.common.url.URLParser;
import com.the.rpc.core.registry.AbstractRegistry;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * zk 注册中心。<br>
 * 这里面会引入 curator，最好是新建个 Module 的，不过现在代码还太简单，不需要
 *
 */
@Slf4j
public class ZkRegistry extends AbstractRegistry {

    //zookeeper客户端
    private final CuratorZkClient zkClient;

    //对url进行编码的工具类, 将url中的特数据字符进行编码
    private static final URLEncoder urlEncoder = URLEncoder.createPathSegment();
    //获取当前系统的默认字符集编码
    private static final Charset charset = Charset.defaultCharset();

    //通过构造方法创建zookeeper客户端对
    public ZkRegistry(URL url) {
        zkClient = new CuratorZkClient(url);
    }

    //进行注册,创建节点
    @Override
    public void doRegister(URL url) {
        //用客户端将此url做成节点
        zkClient.createEphemeralNode(toUrlPath(url));
        //监听创建这个节点
        watch(url);
    }

    //取消节点注册
    @Override
    public void doUnregister(URL url) {
        zkClient.removeNode(toUrlPath(url));
        watch(url);
    }

    //服务发现,查找符合条件url的服务
    @Override
    public List<URL> doLookup(URL condition) {
        List<String> children = zkClient.getChildren(toServicePath(condition));
        List<URL> urls = children.stream()
                .map(s -> URLParser.toURL(URLDecoder.decode(s, charset)))
                .collect(Collectors.toList());
        // 获取到的每个都添加监听
        for (URL url : urls) {
            watch(url);
        }
        return urls;
    }

    /**
     * 转成全路径，包括节点内容
     */
    private String toUrlPath(URL url) {
        return toServicePath(url) + "/" + urlEncoder.encode(url.toFullString(), charset);
    }

    /**
     * 转成服务的路径，例如：/the-rpc/com.the.rpc.demo.service.api.UserService/providers
     */
    private String toServicePath(URL url) {
        return getServiceNameFromUrl(url) + "/" + RegistryConst.PROVIDERS_CATEGORY;
    }

    /**
     * 监听
     */
    private void watch(URL url) {
        String path = toServicePath(url);
        //添加监听器
        zkClient.addListener(path, (type, oldData, data) -> {
            log.info("watch event. type={}, oldData={}, data={}", type, oldData, data);
            //将本地缓存信息更新, 保持最新
            reset(url);
        });
    }

}
