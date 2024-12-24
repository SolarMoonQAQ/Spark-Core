package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.visual_effect.VisualEffectTicker
import cn.solarmoon.spark_core.visual_effect.common.camera_shake.CameraShakeApplier
import net.neoforged.neoforge.common.NeoForge

object SparkClientEvents {

    @JvmStatic
    fun register() {
        add(VisualEffectTicker())
        add(CameraShakeApplier())
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}