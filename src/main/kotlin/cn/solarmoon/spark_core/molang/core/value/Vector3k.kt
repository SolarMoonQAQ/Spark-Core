package cn.solarmoon.spark_core.molang.core.value

import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator
import net.minecraft.Util
import net.minecraft.network.codec.ByteBufCodecs
import org.joml.Vector3f

data class Vector3k(
    val x: IValue,
    val y: IValue,
    val z: IValue
) {

    fun eval(evaluator: ExpressionEvaluator<*>): Vector3f {
        return Vector3f(
            x.evalAsDouble(evaluator).toFloat(),
            y.evalAsDouble(evaluator).toFloat(),
            z.evalAsDouble(evaluator).toFloat()
        )
    }

    companion object {
        val CODEC = IValue.CODEC.listOf()
            .comapFlatMap(
                { Util.fixedSize(it, 3).map { Vector3k(it[0], it[1], it[2]) } },
                { listOf(it.x, it.y, it.z) }
            )

        val STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC)
    }

}
