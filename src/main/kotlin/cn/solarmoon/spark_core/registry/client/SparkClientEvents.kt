package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.animation.renderer.GeoLivingEntityRenderer
import cn.solarmoon.spark_core.local_control.LocalControlApplier
import cn.solarmoon.spark_core.registry.common.SparkEntityStates
import cn.solarmoon.spark_core.visual_effect.VisualEffectTicker
import cn.solarmoon.spark_core.visual_effect.common.camera_shake.CameraShakeApplier
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.common.NeoForge

object SparkClientEvents {

    @JvmStatic
    fun register() {
        add(VisualEffectTicker)
        add(CameraShakeApplier)
        add(LocalControlApplier)
    }

    @JvmStatic
    fun test(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(SparkEntityStates.WUKONG.get(), { GeoLivingEntityRenderer(it, 1f) })
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}