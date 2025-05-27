package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.EntityAnimListener
import cn.solarmoon.spark_core.animation.model.EntityModelListener
import cn.solarmoon.spark_core.ik.origin.IKConstraintListener
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent

object SparkDataRegister {

    private fun reg(event: AddReloadListenerEvent) {
        event.addListener(EntityModelListener())
//        event.addListener(EntityAnimListener())
//        event.addListener(IKConstraintListener())
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}