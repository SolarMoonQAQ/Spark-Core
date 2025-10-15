package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.ActivationContext
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class PlayAbilityCommand: AbilityCommand(2) {

    private val skillSuggestions = SuggestionProvider<CommandSourceStack> { context, builder ->
        context.source.entity?.let { entity ->
            SharedSuggestionProvider.suggest(
                entity.abilitySystemComponent.allAbilitySpecs.values.map { spec ->
                    // 显示为 "handle|registryKey"
                    "'[${spec.handle.id}]${spec.abilityType.registryKey}'"
                },
                builder
            )
        }
    }

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("play").then(
                Commands.argument("target", EntityArgument.entity())
                    .then(
                        Commands.argument("ability", StringArgumentType.string())
                            .suggests(skillSuggestions)
                            .executes { executePlaySkill(it) }
                            .then(
                                Commands.argument("activation_context", StringArgumentType.string())
                                    .executes { executePlaySkill(it) }
                            )
                    )
            )
        )
    }

    private fun executePlaySkill(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val raw = StringArgumentType.getString(context, "ability")
        val nbt = try { StringArgumentType.getString(context, "activation_context") } catch (e: Exception) { "" }

        val regex = Regex("""\[(\d+)](.+)""".trimIndent())
        val match = regex.matchEntire(raw)
        if (match == null) {
            source.sendFailure(Component.literal("无效的技能参数: $raw"))
            return 0
        }

        val handleId = match.groupValues[1].toInt() // 第 1 个捕获组 → 数字
        val skillKey = match.groupValues[2]         // 第 2 个捕获组 → 后面的字符串

        // 获取目标实体
        val target = try {
            EntityArgument.getEntity(context, "target")
        } catch (e: Exception) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.skill.play.invalid_target"))
            return 0
        }


        val asc = target.abilitySystemComponent
        val spec = asc.findSpecFromHandle(AbilityHandle(handleId))

        if (spec == null) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.skill.play.unknown", skillKey))
            return 0
        }

        // 对每个目标执行技能
        var successCount = 0
        val activationContext = if (nbt.isEmpty()) ActivationContext.Empty else ActivationContext.CODEC.decode(NbtOps.INSTANCE, TagParser.parseTag(nbt)).orThrow.first
        if (asc.tryActivateAbility(spec.handle, activationContext)) {
            successCount++
        }

        // 反馈结果
        if (successCount == 0) {
            source.sendFailure(Component.translatable("command.${SparkCore.MOD_ID}.skill.play.no_target"))
        } else {
            source.sendSuccess(
                { Component.translatable("command.${SparkCore.MOD_ID}.skill.play.success", skillKey, successCount) },
                true
            )
        }
        return successCount
    }
}