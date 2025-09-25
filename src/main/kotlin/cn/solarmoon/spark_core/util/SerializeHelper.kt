package cn.solarmoon.spark_core.util

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import net.minecraft.Util
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector2i
import org.joml.Vector3f
import java.awt.Color
import kotlin.math.sqrt

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
    val VEC2I_CODEC: Codec<Vector2i> = Codec.INT.listOf().comapFlatMap(
        { Util.fixedSize(it, 2).map { Vector2i(it[0], it[1]) } },
        { listOf(it.x, it.y) }
    )

    @JvmStatic
    val VECTOR3F_COMPRESSED_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Vector3f> {
        // 0.01m 精度
        private val SCALE = 100.0f

        override fun decode(buffer: FriendlyByteBuf): Vector3f {
            val x = buffer.readShort().toFloat() / SCALE
            val y = buffer.readShort().toFloat() / SCALE
            val z = buffer.readShort().toFloat() / SCALE
            return Vector3f(x, y, z)
        }

        override fun encode(buffer: FriendlyByteBuf, value: Vector3f) {
            buffer.writeShort((value.x * SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            buffer.writeShort((value.y * SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            buffer.writeShort((value.z * SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
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
    val QUATERNIONF_COMPRESSED_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Quaternionf> {
        val SCALE = 32767f

        override fun decode(buffer: FriendlyByteBuf): Quaternionf {
            val largestIndex = buffer.readUnsignedByte()
            val signBit = buffer.readBoolean()

            val comps = FloatArray(4)
            for (i in 0..3) {
                if (i.toShort() == largestIndex) continue
                comps[i] = buffer.readShort() / SCALE
            }

            var sumSquares = 0f
            for (i in 0..3) {
                if (i.toShort() == largestIndex) continue
                sumSquares += comps[i] * comps[i]
            }

            val missing = sqrt(1f - sumSquares).let { if (signBit) -it else it }
            comps[largestIndex.toInt()] = missing

            return Quaternionf(comps[0], comps[1], comps[2], comps[3]).normalize()
        }

        override fun encode(buffer: FriendlyByteBuf, value: Quaternionf) {
            val q = Quaternionf(value).normalize()
            val comps = floatArrayOf(q.x, q.y, q.z, q.w)

            val largestIndex = comps.indices.maxByOrNull { kotlin.math.abs(comps[it]) } ?: 0
            val signBit = comps[largestIndex] < 0f

            buffer.writeByte(largestIndex)
            buffer.writeBoolean(signBit)

            for (i in comps.indices) {
                if (i == largestIndex) continue
                val quantized = (comps[i] * SCALE).toInt().coerceIn(-32767, 32767)
                buffer.writeShort(quantized)
            }
        }
    }



    @JvmStatic
    val COLOR_CODEC: Codec<Color> = Codec.FLOAT.listOf().comapFlatMap(
        { list ->
            Util.fixedSize(list, 4).map { floats ->
                Color(floats[0], floats[1], floats[2], floats[3])
            }
        },
        { color ->
            listOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        }
    )

    @JvmStatic
    val COLOR_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Color> {
        override fun decode(buffer: FriendlyByteBuf): Color {
            val r = buffer.readFloat()
            val g = buffer.readFloat()
            val b = buffer.readFloat()
            val a = buffer.readFloat()
            return Color(r, g, b, a)
        }

        override fun encode(buffer: FriendlyByteBuf, value: Color) {
            buffer.writeFloat(value.red / 255f)
            buffer.writeFloat(value.green / 255f)
            buffer.writeFloat(value.blue / 255f)
            buffer.writeFloat(value.alpha / 255f)
        }
    }

}

fun <B: ByteBuf, T> B.readNullable(codec: StreamCodec<B, T>): T? {
    return if (this.readBoolean()) {
        codec.decode(this)
    } else {
        null
    }
}

fun <B: ByteBuf, T> B.writeNullable(value: T?, codec: StreamCodec<B, T>) {
    if (value != null) {
        this.writeBoolean(true)
        codec.encode(this, value)
    } else {
        this.writeBoolean(false)
    }
}