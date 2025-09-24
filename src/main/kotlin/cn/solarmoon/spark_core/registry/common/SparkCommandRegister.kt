package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.command.*
import net.minecraft.commands.Commands
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object SparkCommandRegister {

    private fun reg(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("spark")
                .then(PlaySkillCommand().create(event.buildContext))
                .then(GetTagCommand().create(event.buildContext))
                .then(IKDebugCommand().create(event.buildContext))
                .then(ReloadPackageCommand().create(event.buildContext))
        )

    }

    private fun clientReg(event: RegisterClientCommandsEvent){
        val dispatcher = event.dispatcher
        dispatcher.register(
            Commands.literal("spark")
                .then(
                    Commands.literal("client")
                        .then(
                            Commands.literal("deps")

                        )
                )
        )
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
        NeoForge.EVENT_BUS.addListener(::clientReg)
    }

}