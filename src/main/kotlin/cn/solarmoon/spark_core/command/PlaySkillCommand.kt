package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillManager
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component

class PlaySkillCommand: SkillCommand(2) {

    private val skillSuggestions = SuggestionProvider<CommandSourceStack> { context, builder ->
        SharedSuggestionProvider.suggestResource(SkillManager.keys, builder)
    }

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("play").then(
                Commands.argument("targets", EntityArgument.entities()) // 添加目标选择器
                    .then(
                        Commands.argument("skill_id", ResourceLocationArgument.id())
                            .suggests(skillSuggestions)
                            .executes { executePlaySkill(it) }
                    )
            )
        )
    }

    private fun executePlaySkill(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val skillId = ResourceLocationArgument.getId(context, "skill_id")
        val level = source.level

        // 获取目标实体
        val targets = try {
            EntityArgument.getEntities(context, "targets")
        } catch (e: Exception) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.skill.play.invalid_target"))
            return 0
        }

        // 验证技能存在
        if (!SkillManager.containsKey(skillId)) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.skill.play.unknown", skillId.toString()))
            return 0
        }

        // 对每个目标执行技能
        var successCount = 0
        for (entity in targets) {
            val skill = SkillManager.get(skillId)!!.createSkill(entity, level, true)
            skill?.apply {
                if (isActivated) {
                    successCount++
                }
            }
        }

        // 反馈结果
        if (successCount == 0) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.skill.play.no_target"))
        } else {
            source.sendSuccess(
                { Component.translatable("command.${SparkCore.MOD_ID}.skill.play.success", skillId.toString(), successCount) },
                true
            )
        }
        return successCount
    }

}