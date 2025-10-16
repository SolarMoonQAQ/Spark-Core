package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.gas.GameplayTag
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.codecs.RecordCodecBuilder

class HasTagCondition(
    val tag: GameplayTag
): StateCondition {

    override val codec = CODEC

    override fun check(controller: StateGraphController): Boolean {
        val tags = controller.tags
        return tag in tags
    }

    companion object {
        val CODEC = RecordCodecBuilder.mapCodec {
            it.group(
                GameplayTag.CODEC.fieldOf("tag").forGetter(HasTagCondition::tag)
            ).apply(it, ::HasTagCondition)
        }
    }
}
