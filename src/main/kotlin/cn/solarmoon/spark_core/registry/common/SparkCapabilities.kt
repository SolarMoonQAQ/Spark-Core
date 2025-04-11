package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.renderer.GeoItemRenderer
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.ItemCapability
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent

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

    class Test: Item(Properties()) {

    }

    class Ex: IClientItemExtensions {
        private val renderer = GeoItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }

    private fun regM(event: RegisterClientExtensionsEvent) {
        event.registerItem(Ex(), TEST)
    }

    private fun applyToItem(event: RegisterCapabilitiesEvent) {
        BuiltInRegistries.ITEM.forEach {
            event.registerItem(
                ITEM_ANIMATABLE,
                { stack, level -> ItemAnimatable(stack, level) },
                it
            )
        }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::applyToItem)
        bus.addListener(::regM)
    }

}