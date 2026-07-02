package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 进入状态时使用 [SparkCore.LOGGER] 输出指定调试消息。
 *
 * 不依赖任何特定控制器上下文，可通用于所有状态机。
 */
class DebugOutputAction(
    val message: String
) : StateAction {

    override val codec = CODEC

    override fun execute(controller: StateGraphController) {
        SparkCore.LOGGER.info("[DebugOutputAction] $message")
    }

    companion object {
        val CODEC: MapCodec<DebugOutputAction> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("message").forGetter(DebugOutputAction::message)
            ).apply(it, ::DebugOutputAction)
        }
    }

}
