package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.ICustomModelItem
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.model.ModelIndex
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.ItemCapability

object SparkCapabilities {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val ITEM_ANIMATABLE = SparkCore.REGISTER.itemCapability<ItemAnimatable, Level>("item_animatable")

}