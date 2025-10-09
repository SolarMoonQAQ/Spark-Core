package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets
import kotlin.collections.component1
import kotlin.collections.component2

class AnimStateModule: SparkPackModule {

    override val id: String = "anim_state"

    override fun onStart(isClientSide: Boolean) {
        OAnimStateMachineSet.ORIGINS.clear()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (pathSegments.size < 2) throw IllegalArgumentException("动画状态机的文件路径必须指向一个具体的模型名称（如：animations/minecraft/player/test.json 指向名为 minecraft:player 的模型，test.json为该模型下的动画）")
        val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
        val animationSet = OAnimStateMachineSet.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
        val nameSpace = if (pathSegments.size > 1) {
            pathSegments[0]
        } else {
            SparkCore.MOD_ID
        }
        val path = if (pathSegments.size >= 2) {
            pathSegments.subList(1, pathSegments.size).joinToString("/")
        } else {
            fileName.removeSuffix(".json")
        }
        val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
        OAnimStateMachineSet.ORIGINS.getOrPut(id) { OAnimStateMachineSet(mutableMapOf()) }.animationControllers.putAll(animationSet.animationControllers)
    }

    override fun onFinish(isClientSide: Boolean) {
        val logMsg = buildString {
            append("\n\uD83E\uDDD1\u200D\uD83E\uDDBD已为 ${OAnimStateMachineSet.ORIGINS.size} 个状态机读取到动画控制器\uD83E\uDDD1\u200D\uD83E\uDDBD\n")
            OAnimStateMachineSet.ORIGINS.forEach { (modelId, set) ->
                append("✅$modelId: [")
                append(set.animationControllers.keys.joinToString(", "))
                append("]\n")
            }
        }
        SparkCore.logger("动画状态机加载器").info(logMsg)
    }

}