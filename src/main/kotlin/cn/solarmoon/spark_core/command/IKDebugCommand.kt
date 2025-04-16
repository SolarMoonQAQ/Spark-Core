package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.ik.visualizer.IKDebugRenderer
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Command to toggle the IK debug visualization.
 * Usage: /ikdebug
 */
// Pass required arguments to BaseCommand constructor
class IKDebugCommand : BaseCommand("ikdebug", 0) { // "ikdebug" is the head, 0 is the permission level

    override fun putExecution(context: CommandBuildContext) {
        builder.executes(::enableIKDebug)
    }

    private fun enableIKDebug(context: CommandContext<CommandSourceStack>): Int {
        // Toggle the enabled state
        SparkVisualEffects.IK.isEnabled = !SparkVisualEffects.IK.isEnabled

        // Send feedback message to the command source
        val status = if (SparkVisualEffects.IK.isEnabled) "enabled" else "disabled"
        context.source.sendSuccess( { Component.literal("IK Debug visualization $status.") }, true) // Use supplier for lazy evaluation

        return 1 // Return 1 for success
    }
}