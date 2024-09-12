package com.huangdada.rpc.core.serializer;

//序列化器
public interface Serializer {
    /**
     *序列化
     * @param o 需要序列化的对象
     * @return 返回完成字节流的字节数组
     */
    byte[] serialize(Object o);

    /**
     * 反序列化
     * @param bytes 需要反序列化的字节数组
     * @return 返回反序列完的对象
     */
     <T> T deserialize(byte[] bytes,  Class<T> clazz);
}
