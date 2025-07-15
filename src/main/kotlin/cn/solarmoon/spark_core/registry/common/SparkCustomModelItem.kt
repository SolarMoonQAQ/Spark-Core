package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.renderer.GeoItemRenderer
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent

object SparkCustomModelItem {
    @JvmStatic
    @SubscribeEvent
    private fun regCustomModel(event: RegisterClientExtensionsEvent) {
        //在此注册拥有自定义模型物品的渲染器，物品需要继承ICustomModelItem接口
        //Register custom model item renderer here, items need to implement ICustomModelItem interface
        event.registerItem(
            CustomModelItemExtension(),
            SparkCapabilities.TEST
        )
    }

    class CustomModelItemExtension : IClientItemExtensions {
        private val renderer = GeoItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::regCustomModel)
    }
}