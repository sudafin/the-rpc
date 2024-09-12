package com.the.rpc.common.extension;

import com.the.rpc.common.consts.URLKeyConst;

import java.lang.annotation.*;

/**
 * 被此注解标记的类，表示是一个扩展接口
 *
 */
@Documented
//注解用于类, 接口, 枚举
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SPI {
    /**
     * 默认扩展类全路径
     *
     * @return 默认不填是 default
     */
    String value() default URLKeyConst.DEFAULT;
}
