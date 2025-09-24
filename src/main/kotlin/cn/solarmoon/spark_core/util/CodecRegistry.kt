package cn.solarmoon.spark_core.util

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
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
        register(Vector3f::class, SerializeHelper.VECTOR3F_STREAM_CODEC)
        register(Quaternionf::class, SerializeHelper.QUATERNIONF_STREAM_CODEC)
    }

    fun <T : Any> register(type: KClass<T>, codec: StreamCodec<*, T>) {
        map[type] = codec
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): StreamCodec<*, T> {
        return map[type] as? StreamCodec<*, T>
            ?: error("未注册类型 ${type.simpleName} 的 codec")
    }

}
