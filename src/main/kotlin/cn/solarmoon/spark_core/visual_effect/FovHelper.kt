package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object FovHelper {

    var fov: Double = 70.0
    var height = 400

    @SubscribeEvent
    fun onLevelTick(event: ClientTickEvent.Pre) {
        val mc = Minecraft.getInstance()
        if (mc.gameRenderer.mainCamera != null)
            fov = mc.gameRenderer.getFov(mc.gameRenderer.mainCamera, 1f, true)
        height = mc.window.height
    }
}