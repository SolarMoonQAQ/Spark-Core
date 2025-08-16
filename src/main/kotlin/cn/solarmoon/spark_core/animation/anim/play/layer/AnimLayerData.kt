package cn.solarmoon.spark_core.animation.anim.play.layer

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class AnimLayerData(
    val weight: Double = 1.0,
    val enterTransitionTime: Int = 7,
    val exitTransitionTime: Int = 7,
    val boneMask: BoneMask = BoneMask(),
    val blendMode: BlendMode = BlendMode.OVERRIDE
) {

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, AnimLayerData::weight,
            ByteBufCodecs.INT, AnimLayerData::enterTransitionTime,
            ByteBufCodecs.INT, AnimLayerData::exitTransitionTime,
            BoneMask.STREAM_CODEC, AnimLayerData::boneMask,
            BlendMode.STREAM_CODEC, AnimLayerData::blendMode,
            ::AnimLayerData
        )
    }

}
