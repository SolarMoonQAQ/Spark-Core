package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.entry_builder.createWithCodec
import cn.solarmoon.spark_core.particle.AnimatableShadowParticle
import cn.solarmoon.spark_core.particle.SpaceWarpParticle
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BedBlockEntity

object SparkParticles {
    @JvmStatic
    fun register() {}

    val ANIMATABLE_SHADOW = SparkCore.REGISTER.particleType {
        id = "animatable_shadow"
        factory = { createWithCodec(true, AnimatableShadowParticle.Option::codec, AnimatableShadowParticle.Option::streamCodec) }
    }

    val SPACE_WARP = SparkCore.REGISTER.particleType {
        id = "space_warp"
        factory = { createWithCodec(true, SpaceWarpParticle.Option::codec, SpaceWarpParticle.Option::streamCodec) }
    }

}