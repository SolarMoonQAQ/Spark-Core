package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class AnimationModule: SparkPackModule {

    override val id: String = "animations"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (fromServer) OAnimationSet.ORIGINS.clear()
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean
    ) {
        if (!fromServer) return
        if (pathSegments.size < 2) {
            throw PackContentException(
                Component.translatableWithFallback(
                    "spark_core.pack.error.animation_path",
                    "Animation path must point to a specific model (e.g. minecraft/animations/entity/player/test.json).",
                )
            )
        }
        val json = PackCodec.parse(content)
        val animationSet = PackCodec.read(OAnimationSet.CODEC, json)
        val id = ResourceLocation.fromNamespaceAndPath(namespace, pathSegments[1])
        OAnimationSet.ORIGINS.getOrPut(ModelIndex(pathSegments[0], id)) { OAnimationSet.EMPTY }.animations.putAll(animationSet.animations)
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
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