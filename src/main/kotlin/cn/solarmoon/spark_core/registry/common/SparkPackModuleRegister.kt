package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.resource2.modules.AnimationModule
import cn.solarmoon.spark_core.resource2.modules.ModelModule
import net.neoforged.neoforge.common.NeoForge

object SparkPackModuleRegister {

    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(ModelModule())
        event.register(AnimationModule())
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}