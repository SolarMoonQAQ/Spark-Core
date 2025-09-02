package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.resource2.SparkPackModule
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class AnimationModule: SparkPackModule {

    override val moduleName: String = "animations"

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
        OAnimationSet.ORIGINS[id] = animationSet
    }

    override fun onFinish() {
        SparkCore.LOGGER.info("已为 ${OAnimationSet.ORIGINS.size} 个模型读取到动画集，共包含 ${OAnimationSet.ORIGINS.values.sumOf { it.animations.size } } 个动画")
    }

}