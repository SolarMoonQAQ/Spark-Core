package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource2.SparkPackLoader
import cn.solarmoon.spark_core.resource2.SparkPackResourceLoader
import cn.solarmoon.spark_core.resource2.sync.SparkPackageReloadPayload
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

class ReloadPackageCommand: PackageCommand(2) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("reload").executes { executeReloadModule(it) }
        )
    }

    private fun executeReloadModule(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        source.sendSuccess(
            { Component.translatable("command.${SparkCore.MOD_ID}.package.reload.progressing") },
            true
        )
        SparkPackResourceLoader.reload()
        SparkPackLoader.readPackageGraph()
        SparkPackLoader.readPackageContent(false)
        PacketDistributor.sendToAllPlayers(SparkPackageReloadPayload(SparkPackLoader.graph))
        source.sendSuccess(
            { Component.translatable("command.${SparkCore.MOD_ID}.package.reload.success") },
            true
        )
        return 1
    }

}