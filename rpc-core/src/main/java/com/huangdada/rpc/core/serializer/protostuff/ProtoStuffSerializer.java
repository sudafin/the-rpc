package com.huangdada.rpc.core.serializer.protostuff;

import com.huangdada.rpc.core.serializer.Serializer;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

/**
 * 基于Protobuf而扩展的ProtoStuff, 它用于Java程序中, 不用在额外的使用.proto文件去执行序列化/反序列化操作
 */
public class ProtoStuffSerializer implements Serializer {
    //创建一个链表缓冲区并开初始大小512字节大小的空间LinkedBuffer,LinkedBuffer可以提高序列化性能的一个临时缓冲区。它可以避免频繁的内存分配
    private static final LinkedBuffer BUFFER = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
    /**
     * 序列化需要传输的数据对象
     * @param object 要序列化的对象
     * @return 返回一个序列化完的数据对象
     */
    @Override
    public byte[] serialize(Object object) {
        // Protostuff 库使用 Schema 来描述类的结构，包括类的字段以及字段的序列化规则。这一过程是需要对象的类反射获取,用于将信息转到缓冲区生成字节流
        Schema  schema = RuntimeSchema.getSchema(object.getClass());
        try{
            //序列化对象转换为字节流二进制
            return ProtostuffIOUtil.toByteArray(object, schema , BUFFER);
        }finally {
            //关闭缓冲区
            BUFFER.clear();
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes,  Class<T> clazz) {
        //获取该类的结构信息schema, 依旧通过反射获取
        Schema<T> schema = RuntimeSchema.getSchema(clazz);
        //创建一个T类型的空对象用于接收对象
        T obj = schema.newMessage();
        //解析字节数组并将数据转给obj,并通过schema将字节流映射会对象的各个字段中
        ProtostuffIOUtil.mergeFrom(bytes, obj, schema);
        return obj;
    }
}
