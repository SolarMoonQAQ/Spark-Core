package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.particle.client.ParticleDefinitionLoader
import cn.solarmoon.spark_core.particle.client.ParticleEmitterManager
import cn.solarmoon.spark_core.api.ParticleEffects
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * 粒子系统调试指令。
 * /spark particle spawn <effectId> [x] [y] [z] [rotX] [rotY] [rotZ] [scale]
 * /spark particle list [filter]
 * /spark particle clear
 */
class ParticleCommand : BaseCommand("particle", 2) {

    /** 粒子 ID 补全建议 */
    private val particleSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        val ids = ParticleDefinitionLoader.getInstance().allDefinitions.keys.map { it.toString() }
        SharedSuggestionProvider.suggest(ids, builder)
    }

    override fun putExecution(context: CommandBuildContext) {
        // spawn <effectId> [pos] [rotX] [rotY] [rotZ] [scale]
        builder.then(
            Commands.literal("spawn")
                .then(
                    Commands.argument("effectId", ResourceLocationArgument.id())
                        .suggests(particleSuggestions)
                        .executes { executeSpawn(it, null, 0f, 0f, 0f, 1f) }
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .executes { executeSpawn(it, Vec3Argument.getVec3(it, "pos"), 0f, 0f, 0f, 1f) }
                                .then(
                                    Commands.argument("rotX", FloatArgumentType.floatArg(-180f, 180f))
                                        .then(
                                            Commands.argument("rotY", FloatArgumentType.floatArg(-180f, 180f))
                                                .then(
                                                    Commands.argument("rotZ", FloatArgumentType.floatArg(-180f, 180f))
                                                        .executes {
                                                            executeSpawn(
                                                                it, Vec3Argument.getVec3(it, "pos"),
                                                                FloatArgumentType.getFloat(it, "rotX"),
                                                                FloatArgumentType.getFloat(it, "rotY"),
                                                                FloatArgumentType.getFloat(it, "rotZ"),
                                                                1f
                                                            )
                                                        }
                                                        .then(
                                                            Commands.argument("scale", FloatArgumentType.floatArg(0f, 100f))
                                                                .executes {
                                                                    executeSpawn(
                                                                        it, Vec3Argument.getVec3(it, "pos"),
                                                                        FloatArgumentType.getFloat(it, "rotX"),
                                                                        FloatArgumentType.getFloat(it, "rotY"),
                                                                        FloatArgumentType.getFloat(it, "rotZ"),
                                                                        FloatArgumentType.getFloat(it, "scale")
                                                                    )
                                                                }
                                                        )
                                                )
                                        )
                                )
                        )
                )
        )

        // list [filter]
        builder.then(
            Commands.literal("list")
                .executes(::executeList)
                .then(
                    Commands.argument("filter", StringArgumentType.word())
                        .executes(::executeList)
                )
        )

        // clear
        builder.then(
            Commands.literal("clear")
                .executes { executeClear(it) }
        )
    }

    /**
     * 在指定位置生成粒子效果（客户端命令，使用 unsidedLevel 双端通用）。
     *
     * @param pos   位置，null 时使用命令源位置
     * @param rotX  X 轴旋转（度），与 rotY/rotZ 按 YXZ 顺序组合为四元数
     * @param rotY  Y 轴旋转（度）
     * @param rotZ  Z 轴旋转（度）
     * @param scale 统一缩放
     */
    private fun executeSpawn(context: CommandContext<CommandSourceStack>,
                             pos: Vec3?,
                             rotX: Float, rotY: Float, rotZ: Float,
                             scale: Float): Int {
        val source = context.source
        val effectId = ResourceLocationArgument.getId(context, "effectId")
        val level = source.unsidedLevel
        val position = pos ?: (source.position ?: return 0)

        // 将 YXZ 欧拉角（度）转为四元数
        val rotation = Quaternionf().rotationYXZ(
            Math.toRadians(rotY.toDouble()).toFloat(),
            Math.toRadians(rotX.toDouble()).toFloat(),
            Math.toRadians(rotZ.toDouble()).toFloat()
        )
        val scaleVec = Vec3(scale.toDouble(), scale.toDouble(), scale.toDouble())

        ParticleEffects.burst(level, effectId, position, rotation, scaleVec)

        source.sendSuccess(
            { Component.literal("已触发粒子效果: $effectId  位置: ${position.x}, ${position.y}, ${position.z}") },
            true
        )
        return Command.SINGLE_SUCCESS
    }

    /**
     * 列出所有已加载的粒子定义。
     */
    private fun executeList(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        val filter = try {
            StringArgumentType.getString(context, "filter")
        } catch (_: Exception) {
            ""
        }

        val definitions = ParticleDefinitionLoader.getInstance().allDefinitions
        val filtered = if (filter.isEmpty()) {
            definitions.keys.toList()
        } else {
            definitions.keys.filter { it.toString().contains(filter, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            source.sendSuccess(
                { Component.literal("未找到匹配的粒子定义") },
                false
            )
            return 0
        }

        source.sendSuccess(
            { Component.literal("已加载 ${definitions.size} 个粒子定义，匹配 ${filtered.size} 个:") },
            false
        )
        filtered.forEach { id ->
            val def = definitions[id]
            val tex = def?.description?.texture?.path?.substringAfterLast('/') ?: "?"
            source.sendSuccess(
                { Component.literal("  §e$id  §7($tex)") },
                false
            )
        }
        return Command.SINGLE_SUCCESS
    }

    /**
     * 清空所有活跃的发射器。
     */
    private fun executeClear(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source

        val count = ParticleEmitterManager.getInstance().emitterCount
        ParticleEmitterManager.getInstance().clear()

        source.sendSuccess(
            { Component.literal("已清空 $count 个粒子发射器") },
            true
        )
        return Command.SINGLE_SUCCESS
    }
}
