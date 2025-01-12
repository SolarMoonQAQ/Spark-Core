package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.animation.model.origin.OUVUnion.FaceUV
import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Direction
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec2

data class OUVUnion(
    val uv: Vec2?,
    val faceUV: Map<Direction, FaceUV>?
) {

    val isBoxUv get() = uv != null

    data class FaceUV(
        val uv: Vec2,
        val uvSize: Vec2
    ) {
        companion object {
            @JvmStatic
            val CODEC: Codec<FaceUV> = RecordCodecBuilder.create { instance ->
                instance.group(
                    SerializeHelper.VEC2_CODEC.fieldOf("uv").forGetter { it.uv },
                    SerializeHelper.VEC2_CODEC.fieldOf("uv_size").forGetter { it.uvSize }
                ).apply(instance, ::FaceUV)
            }

            @JvmStatic
            val STREAM_CODEC = StreamCodec.composite(
                SerializeHelper.VEC2_STREAM_CODEC, FaceUV::uv,
                SerializeHelper.VEC2_STREAM_CODEC, FaceUV::uvSize,
                ::FaceUV
            )
        }
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<OUVUnion> = Codec.either(
            SerializeHelper.VEC2_CODEC,
            Codec.unboundedMap(Direction.CODEC, FaceUV.CODEC)
        ).xmap(
            { either ->
                either.map(
                    { uv -> OUVUnion(uv, null) },
                    { faceUV -> OUVUnion(null, faceUV) }
                )
            },
            { uvUnion ->
                uvUnion.uv?.let { Either.left(it) } ?: Either.right(uvUnion.faceUV!!)
            }
        )

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OUVUnion> {
            override fun decode(buffer: FriendlyByteBuf): OUVUnion {
                val isBoxUv = buffer.readBoolean()
                return if (isBoxUv) {
                    val uv = SerializeHelper.VEC2_STREAM_CODEC.decode(buffer)
                    OUVUnion(uv, null)
                } else {
                    val faceUV = mutableMapOf<Direction, FaceUV>()
                    val size = buffer.readInt()
                    for (i in 0 until size) {
                        val direction = Direction.STREAM_CODEC.decode(buffer)
                        val faceUVValue = FaceUV.STREAM_CODEC.decode(buffer)
                        faceUV[direction] = faceUVValue
                    }
                    OUVUnion(null, faceUV)
                }
            }

            override fun encode(buffer: FriendlyByteBuf, value: OUVUnion) {
                buffer.writeBoolean(value.isBoxUv)
                if (value.isBoxUv) {
                    SerializeHelper.VEC2_STREAM_CODEC.encode(buffer, value.uv!!)
                } else {
                    buffer.writeInt(value.faceUV!!.size)
                    for ((direction, faceUVValue) in value.faceUV) {
                        Direction.STREAM_CODEC.encode(buffer, direction)
                        FaceUV.STREAM_CODEC.encode(buffer, faceUVValue)
                    }
                }
            }
        }
    }

}