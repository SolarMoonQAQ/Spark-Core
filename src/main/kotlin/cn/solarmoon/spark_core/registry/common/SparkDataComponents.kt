package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatableItem
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3

object SparkDataComponents {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val ANIMATABLE = SparkCore.REGISTER.dataComponent<ItemAnimatable>()
        .id("animatable")
        .build {
            it.persistent(ItemAnimatable.CODEC)
                .networkSynchronized(ItemAnimatable.STREAM_CODEC)
                .cacheEncoding()
                .build()
        }

}