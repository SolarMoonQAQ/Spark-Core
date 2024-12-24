package cn.solarmoon.spark_core.animation.anim.part

import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class Loop {
    ONCE, HOLD_ON_LAST_FRAME, TRUE;

    companion object {
        @JvmStatic
        val CODEC: Codec<Loop> = Codec.STRING.xmap(
            { valueOf(it.uppercase()) },
            { it.name }
        )

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, Loop> {
            override fun decode(buffer: FriendlyByteBuf): Loop {
                return buffer.readEnum(Loop::class.java)
            }

            override fun encode(buffer: FriendlyByteBuf, value: Loop) {
                buffer.writeEnum(value)
            }
        }
    }

}