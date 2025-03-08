package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.command.PlaySkillCommand
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object SparkCommandRegister {

    private fun reg(event: RegisterCommandsEvent) {
        event.dispatcher.register(PlaySkillCommand().builder)
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}