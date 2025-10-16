package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.MapCodec
import java.util.function.Function

interface StateAction {

    fun execute(controller: StateGraphController)

    val codec: MapCodec<out StateAction>

    companion object {
        val CODEC = SparkRegistries.STATE_ACTION_CODEC.byNameCodec()
            .dispatch(
                StateAction::codec,
                Function.identity()
            )
    }

}