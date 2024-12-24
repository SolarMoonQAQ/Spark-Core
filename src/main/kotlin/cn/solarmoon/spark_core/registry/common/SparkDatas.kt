package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.EntityAnimListener
import cn.solarmoon.spark_core.animation.model.EntityModelListener
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent

object SparkDatas {

    private fun reg(event: AddReloadListenerEvent) {
        event.addListener(EntityModelListener())
        event.addListener(EntityAnimListener())
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}