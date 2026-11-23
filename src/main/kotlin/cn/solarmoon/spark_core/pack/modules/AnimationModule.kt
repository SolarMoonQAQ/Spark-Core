package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 动画集加载器。
 *
 * 目录口径是 {@code <namespace>/animations/<type>/<set_name>/<file>.json}：前两段决定资源键
 * （类型 + 集合名），文件名不参与键。同一集合的多个文件按读取顺序合并，后面的覆盖前面的。
 *
 * 读取阶段只写本模块的暂存表，全部文件读完后 [onFinish] 整表发布；运行期因此不会看到
 * 读到一半的集合，资源重载后同一键的对象引用变化也能被动画体观察到。
 */
class AnimationModule: SparkPackModule {

    override val id: String = "animations"

    /** 本轮读取的暂存动画集；键与发布表一致。 */
    private val staged = linkedMapOf<ModelIndex, OAnimationSet>()

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        // 上一批发布表保持可见，直到本批读取完成再整表替换：重载期间运行期读到的始终是一份完整集合。
        staged.clear()
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
        staged.getOrPut(ModelIndex(pathSegments[0], id)) { OAnimationSet(linkedMapOf()) }
            .animations.putAll(animationSet.animations)
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        OAnimationSet.publish(staged)
        staged.clear()
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
