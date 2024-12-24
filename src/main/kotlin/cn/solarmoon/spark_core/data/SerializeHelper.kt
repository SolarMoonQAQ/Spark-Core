package cn.solarmoon.spark_core.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.Util
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * 补充一些解码方法
 */
object SerializeHelper {

    @JvmStatic
    val STRING_SET_STREAM_CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.collection { mutableSetOf() })

    @JvmStatic
    val VEC2_CODEC: Codec<Vec2> = Codec.FLOAT.listOf().comapFlatMap(
        { Util.fixedSize(it, 2).map { Vec2(it[0], it[1]) } },
        { listOf(it.x, it.y) }
    )

    @JvmStatic
    val VECTOR3F_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Vector3f> {
        override fun decode(buffer: FriendlyByteBuf): Vector3f {
            return Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }

        override fun encode(buffer: FriendlyByteBuf, value: Vector3f) {
            buffer.writeFloat(value.x)
            buffer.writeFloat(value.y)
            buffer.writeFloat(value.z)
        }
    }

    @JvmStatic
    val VEC3_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Vec3> {
        override fun decode(buffer: FriendlyByteBuf): Vec3 {
            val x = buffer.readDouble()
            val y = buffer.readDouble()
            val z = buffer.readDouble()
            return Vec3(x, y, z)
        }

        override fun encode(buffer: FriendlyByteBuf, value: Vec3) {
            buffer.writeDouble(value.x)
            buffer.writeDouble(value.y)
            buffer.writeDouble(value.z)
        }
    }

    @JvmStatic
    val VEC2_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Vec2> {
        override fun decode(buffer: FriendlyByteBuf): Vec2 {
            return Vec2(buffer.readFloat(), buffer.readFloat())
        }

        override fun encode(buffer: FriendlyByteBuf, value: Vec2) {
            buffer.writeFloat(value.x)
            buffer.writeFloat(value.y)
        }
    }

    @JvmStatic
    val QUATERNIONF_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Quaternionf> {
        override fun decode(buffer: FriendlyByteBuf): Quaternionf {
            return Quaternionf(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }

        override fun encode(buffer: FriendlyByteBuf, value: Quaternionf) {
            buffer.writeFloat(value.x)
            buffer.writeFloat(value.y)
            buffer.writeFloat(value.z)
            buffer.writeFloat(value.w)
        }
    }

}

fun <T> RegistryFriendlyByteBuf.readNullable(codec: StreamCodec<FriendlyByteBuf, T>): T? {
    return if (this.readBoolean()) {
        codec.decode(this)
    } else {
        null
    }
}

fun <T> RegistryFriendlyByteBuf.writeNullable(value: T?, codec: StreamCodec<FriendlyByteBuf, T>) {
    if (value != null) {
        this.writeBoolean(true)
        codec.encode(this, value)
    } else {
        this.writeBoolean(false)
    }
}