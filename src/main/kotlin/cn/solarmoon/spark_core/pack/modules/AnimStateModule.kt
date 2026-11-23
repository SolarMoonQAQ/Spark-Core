package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.AnimResourceKey
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 动画控制器集加载器。
 *
 * 目录口径是 {@code <namespace>/controllers/<type>/<set_name>/<file>.json}：前两段决定资源键
 * （类型 + 集合名），文件名不参与键，集合成员的键是文件里的控制器名。同一集合的多个文件合并。
 *
 * 读取阶段只写本模块的暂存表，全部文件读完后 [onFinish] 整表发布；运行期不会读到半成品。
 */
class AnimStateModule: SparkPackModule {

    override val id: String = "controllers"

    /** 本轮读取的暂存控制器集；键与发布表一致。 */
    private val staged = linkedMapOf<AnimResourceKey, OAnimStateMachineSet>()

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        // 上一批发布表保持可见，直到本批读取完成再整表替换。
        staged.clear()
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (!fromServer) return
        if (pathSegments.size < 2) {
            throw PackContentException(
                Component.translatableWithFallback(
                    "spark_core.pack.error.controller_path",
                    "Controller path must point to a specific model set (e.g. minecraft/controllers/entity/player/test.json).",
                )
            )
        }
        val json = PackCodec.parse(content)
        val animationSet = PackCodec.read(OAnimStateMachineSet.CODEC, json)
        val location = ResourceLocation.fromNamespaceAndPath(namespace, pathSegments[1])
        staged.getOrPut(AnimResourceKey(pathSegments[0], location)) { OAnimStateMachineSet(linkedMapOf()) }
            .animationControllers.putAll(animationSet.animationControllers)
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        OAnimStateMachineSet.publish(staged)
        staged.clear()
        val logMsg = buildString {
            append("\n\uD83E\uDDD1\u200D\uD83E\uDDBD已为 ${OAnimStateMachineSet.ORIGINS.size} 个状态机读取到动画控制器\uD83E\uDDD1\u200D\uD83E\uDDBD\n")
            OAnimStateMachineSet.ORIGINS.forEach { (modelId, set) ->
                append("✅$modelId: [")
                append(set.animationControllers.keys.joinToString(", "))
                append("]\n")
            }
        }
        SparkCore.logger("动画控制器加载器").info(logMsg)
    }

}
