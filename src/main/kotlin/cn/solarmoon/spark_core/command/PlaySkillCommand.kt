package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component

class PlaySkillCommand: BaseCommand("sparkskill", 2, true) {

    private val skillSuggestions = SuggestionProvider<CommandSourceStack> { context, builder ->
        val registry = context.source.registryAccess().registry(SparkRegistries.SKILL_TYPE).get()
        SharedSuggestionProvider.suggestResource(registry.keySet(), builder)
    }

    override fun putExecution() {
        builder.then(
            Commands.argument("targets", EntityArgument.entities()) // 添加目标选择器
                .then(
                    Commands.argument("skill_id", ResourceLocationArgument.id())
                        .suggests(skillSuggestions)
                        .executes { executePlaySkill(it) }
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
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.play_skill.invalid_target"))
            return 0
        }

        // 验证技能存在
        val registry = level.registryAccess().registry(SparkRegistries.SKILL_TYPE).get()
        if (!registry.containsKey(skillId)) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.play_skill.unknown", skillId.toString()))
            return 0
        }

        // 对每个目标执行技能
        var successCount = 0
        for (entity in targets) {
            val skill = registry.get(skillId)!!.createSkill(entity, level, true)
            skill.apply {
                if (isActive) {
                    successCount++
                }
            }
        }

        // 反馈结果
        if (successCount == 0) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.play_skill.no_target"))
        } else {
            source.sendSuccess(
                { Component.translatable("command.${SparkCore.MOD_ID}.play_skill.success", skillId.toString(), successCount) },
                true
            )
        }
        return successCount
    }

}