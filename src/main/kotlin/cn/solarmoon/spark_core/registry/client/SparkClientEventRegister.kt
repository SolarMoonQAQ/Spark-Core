package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.camera.CameraAdjuster
import cn.solarmoon.spark_core.client.gui.screen.ModelEditorScreenTicker
import cn.solarmoon.spark_core.entity.player.PlayerApplier
import cn.solarmoon.spark_core.local_control.LocalController
import cn.solarmoon.spark_core.visual_effect.VisualEffectTicker
import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShakeApplier
import net.neoforged.neoforge.common.NeoForge

object SparkClientEventRegister {

    @JvmStatic
    fun register() {
        add(PlayerApplier.Client)
        add(VisualEffectTicker)
        add(CameraShakeApplier)
        add(LocalController)
        add(ModelEditorScreenTicker)
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}