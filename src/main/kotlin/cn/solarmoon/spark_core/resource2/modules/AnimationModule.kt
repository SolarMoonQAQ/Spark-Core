package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class AnimationModule: SparkPackModule {

    override val id: String = "animations"

    override fun onStart() {
        OAnimationSet.ORIGINS.clear()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        if (pathSegments.size < 2) throw IllegalArgumentException("动画的文件路径必须指向一个具体的模型名称（如：animations/minecraft/player/test.json 指向名为 minecraft:player 的模型，test.json为该模型下的动画）")
        val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
        val animationSet = OAnimationSet.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
        val id = ResourceLocation.fromNamespaceAndPath(pathSegments[0], pathSegments[1])
        OAnimationSet.ORIGINS.getOrPut(id) { OAnimationSet.EMPTY }.animations.putAll(animationSet.animations)
    }

    override fun onFinish() {
        val logMsg = buildString {
            append("\n\uD83E\uDDD1\u200D\uD83E\uDDBD已为 ${OAnimationSet.ORIGINS.size} 个模型读取到动画集\uD83E\uDDD1\u200D\uD83E\uDDBD\n")
            OAnimationSet.ORIGINS.forEach { (modelId, set) ->
                append("✅$modelId: [")
                append(set.animations.keys.joinToString(", "))
                append("]\n")
            }
        }
        SparkCore.logger("动画加载器").info(logMsg)
    }

}