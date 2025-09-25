package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.physics.component.shape.CollisionShapeType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.reflect.KClass

object CodecRegistry {

    private val map = mutableMapOf<KClass<*>, StreamCodec<*, *>>()

    init {
        register(Int::class, ByteBufCodecs.INT)
        register(Float::class, ByteBufCodecs.FLOAT)
        register(Double::class, ByteBufCodecs.DOUBLE)
        register(String::class, ByteBufCodecs.STRING_UTF8)
        register(Boolean::class, ByteBufCodecs.BOOL)
        register(Vector3f::class, SerializeHelper.VECTOR3F_COMPRESSED_STREAM_CODEC)
        register(Quaternionf::class, SerializeHelper.QUATERNIONF_COMPRESSED_STREAM_CODEC)
        register(CollisionShapeType::class, CollisionShapeType.STREAM_CODEC)
    }

    fun <T : Any> register(type: KClass<T>, codec: StreamCodec<*, T>) {
        map[type] = codec
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): StreamCodec<*, T> {
        return map[type] as? StreamCodec<*, T> ?: error("未注册类型 ${type.simpleName} 的网络流编解码器")
    }

}
