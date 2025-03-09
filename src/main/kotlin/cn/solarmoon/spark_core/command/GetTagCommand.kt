package cn.solarmoon.spark_core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

class GetTagCommand: BaseCommand("gettag", 2) {

    override fun putExecution(context: CommandBuildContext) {
        builder.executes(::executeGetTag)
    }

    private fun executeGetTag(context: CommandContext<CommandSourceStack>): Int {
        val item = context.source.player?.mainHandItem ?: return 0
        val tags = item.tags.toList().toList().map { it.location }
        context.source.sendSuccess({ Component.literal(tags.toString()) }, true)

        return Command.SINGLE_SUCCESS
    }

}