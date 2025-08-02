package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.ICustomModelItem
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.ItemCapability

object SparkCapabilities {

    @JvmStatic
    val ITEM_ANIMATABLE = ItemCapability.create(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "item_animatable"),
        ItemAnimatable::class.java,
        Level::class.java
    )

    @JvmStatic
    val TEST = SparkCore.REGISTER.item<Test>()
        .id("test")
        .bound { Test() }
        .build()

    class Test: Item(Properties()), ICustomModelItem {
        override fun getModelIndex(itemStack: ItemStack, level: Level, context: ItemDisplayContext): ModelIndex {
            return if (context.firstPerson()) ModelIndex(
                ResourceLocation.fromNamespaceAndPath("minecraft", "item/crowbar_first_person.geo"),
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "textures/item/crowbar_first_person.png")
            )
            else ModelIndex(
                ResourceLocation.fromNamespaceAndPath("minecraft", "item/crowbar.geo"),
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "textures/item/crowbar.png")
            )
        }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
//        bus.addListener(::applyToItem)
//        bus.addListener(::regM)
    }

}