package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.command.* // Import all commands from the package
import net.minecraft.commands.Commands
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object SparkCommandRegister {

    private fun reg(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("spark")
                .then(PlaySkillCommand().create(event.buildContext))
                .then(ReloadSkillCommand().create(event.buildContext))
                .then(GetTagCommand().create(event.buildContext))
                .then(IKDebugCommand().create(event.buildContext))
                .then(GetWandCommand().create(event.buildContext))
        )
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}