package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoader
import cn.solarmoon.spark_core.pack.SparkPackResourceLoader
import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import cn.solarmoon.spark_core.pack.sync.SparkPackageReloadPayload
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
        SparkPackLoader.beginSession(PackLoadContext.SERVER_LOCAL)
        SparkPackResourceLoader.reload()
        SparkPackLoader.readPackageGraph(PackLoadContext.SERVER_LOCAL)
        SparkPackLoader.readPackageContent(PackLoadContext.SERVER_LOCAL)
        SparkPackLoader.injectPackageContent(PackLoadContext.SERVER_LOCAL)
        PacketDistributor.sendToAllPlayers(SparkPackageReloadPayload(SparkPackLoader.collectRemote()))
        source.sendSuccess(
            { Component.translatable("command.${SparkCore.MOD_ID}.package.reload.success") },
            true
        )
        return 1
    }

}