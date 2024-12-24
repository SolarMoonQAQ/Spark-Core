package cn.solarmoon.spark_core.animation.anim.play

import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import kotlin.text.uppercase

enum class ModelType {
    ENTITY, BLOCK_ENTITY, ITEM;

    val id = toString().lowercase()

    companion object {
        @JvmStatic
        val CODEC: Codec<ModelType> = Codec.STRING.xmap(
            { ModelType.valueOf(it.uppercase()) },
            { it.name }
        )

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, ModelType> {
            override fun decode(buffer: FriendlyByteBuf): ModelType {
                return buffer.readEnum(ModelType::class.java)
            }

            override fun encode(buffer: FriendlyByteBuf, value: ModelType) {
                buffer.writeEnum(value)
            }
        }
    }

}