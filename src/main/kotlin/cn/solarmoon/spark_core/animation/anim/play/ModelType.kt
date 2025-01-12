package cn.solarmoon.spark_core.animation.anim.play

import com.mojang.serialization.Codec
import net.minecraft.network.codec.ByteBufCodecs

data class ModelType(
    val id: String
) {

    companion object {
        @JvmStatic
        val CODEC = Codec.STRING.xmap({ ModelType(it) }, { it.id })

        @JvmStatic
        val STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map({ ModelType(it) }, { it.id })
    }

}