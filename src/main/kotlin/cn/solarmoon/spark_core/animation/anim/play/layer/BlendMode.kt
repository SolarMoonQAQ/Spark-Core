package cn.solarmoon.spark_core.animation.anim.play.layer

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.util.ByIdMap

enum class BlendMode {
    OVERRIDE, ADDITIVE;

    companion object {
        val STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(
                BlendMode::ordinal,
                BlendMode.entries.toTypedArray(),
                ByIdMap.OutOfBoundsStrategy.ZERO
            ),
            BlendMode::ordinal
        )
    }
}