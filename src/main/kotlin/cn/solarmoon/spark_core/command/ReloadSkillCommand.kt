package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.js.ServerSparkJS
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.skill.JSSkillApi
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.runBlocking
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ReloadSkillCommand: SkillCommand(2) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("reload").executes(::executeReloadSkill)
        )
    }

    private fun executeReloadSkill(context: CommandContext<CommandSourceStack>): Int {
        (SparkJS.ALL[context.source.level.isClientSide]!! as ServerSparkJS).reload("skill")
        return 1
    }

}