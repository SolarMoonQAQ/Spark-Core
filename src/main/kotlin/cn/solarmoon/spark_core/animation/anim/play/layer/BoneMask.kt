package cn.solarmoon.spark_core.animation.anim.play.layer

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

class BoneMask(
    // 骨骼名称到混合权重的映射（0.0-1.0）
    private val boneWeights: Map<String, Double> = emptyMap(),
    private val default: Double = 1.0
) {

    init {
        val wrong = boneWeights.filter { it.value !in 0.0..1.0 }
        if (wrong.isNotEmpty()) {
            throw IllegalArgumentException("骨骼(${wrong.keys})权重只能是[0.0, 1.0]之间的数")
        }
    }

    fun getWeight(boneName: String): Double {
        return boneWeights[boneName] ?: default
    }

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(
                ::LinkedHashMap,
                ByteBufCodecs.STRING_UTF8,
                ByteBufCodecs.DOUBLE
            ), BoneMask::boneWeights,
            ByteBufCodecs.DOUBLE, BoneMask::default,
            ::BoneMask
        )
    }

}