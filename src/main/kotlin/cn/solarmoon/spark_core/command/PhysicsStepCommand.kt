package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.api.SparkLevel
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

class PhysicsStepCommand : BaseCommand("physics", 2) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("step")
                .then(getCommand())
                .then(setCommand())
                .then(minCommand())
                .then(maxCommand())
                .then(resetCommand())
        )
    }

    private fun getCommand() =
        Commands.literal("get")
            .executes {
                val level = SparkLevel.getPhysicsLevel(it.source.unsidedLevel)
                it.source.sendSuccess(
                    {
                        Component.literal(
                            "${level.name} Physics step: current=${level.dynamicRepeat}, " +
                                    "min=${level.minStep}, max=${level.maxStep}"
                        )
                    },
                    false
                )
                1
            }

    private fun setCommand() =
        Commands.literal("set")
            .then(
                Commands.argument("min", IntegerArgumentType.integer(1))
                    .then(
                        Commands.argument("max", IntegerArgumentType.integer(1))
                            .executes {
                                val level = SparkLevel.getPhysicsLevel(it.source.unsidedLevel)

                                val min = IntegerArgumentType.getInteger(it, "min")
                                val max = IntegerArgumentType.getInteger(it, "max")

                                if (min > max) {
                                    it.source.sendFailure(
                                        Component.literal("min must be <= max")
                                    )
                                    return@executes 0
                                }

                                level.minStep = min
                                level.maxStep = max

                                it.source.sendSuccess(
                                    {
                                        Component.literal(
                                            "${level.name} Physics step range set to [$min, $max]"
                                        )
                                    },
                                    true
                                )
                                1
                            }
                    )
            )

    private fun minCommand() =
        Commands.literal("min")
            .then(
                Commands.argument("value", IntegerArgumentType.integer(1))
                    .executes {
                        val level = SparkLevel.getPhysicsLevel(it.source.unsidedLevel)

                        val value = IntegerArgumentType.getInteger(it, "value")
                        level.minStep = value

                        it.source.sendSuccess(
                            { Component.literal("${level.name} Physics minStep set to $value") },
                            true
                        )
                        1
                    }
            )

    private fun maxCommand() =
        Commands.literal("max")
            .then(
                Commands.argument("value", IntegerArgumentType.integer(1))
                    .executes {
                        val level = SparkLevel.getPhysicsLevel(it.source.unsidedLevel)

                        val value = IntegerArgumentType.getInteger(it, "value")
                        level.maxStep = value

                        it.source.sendSuccess(
                            { Component.literal("${level.name} Physics maxStep set to $value") },
                            true
                        )
                        1
                    }
            )

    private fun resetCommand() =
        Commands.literal("reset")
            .executes {
                val level = SparkLevel.getPhysicsLevel(it.source.unsidedLevel)
                level.minStep = level.defaultMinStep
                level.maxStep = level.defaultMaxStep
                it.source.sendSuccess(
                    { Component.literal("${level.name} Physics step range reset to default") },
                    true
                )
                1
            }
}