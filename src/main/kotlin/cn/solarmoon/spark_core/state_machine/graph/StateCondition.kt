package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.state_machine.graph.StateCondition.Reverse
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.function.Function

interface StateCondition {

    val codec: MapCodec<out StateCondition>

    fun check(controller: StateGraphController): Boolean

    companion object {
        val CODEC = SparkRegistries.ACTION_CONDITION_CODEC.byNameCodec()
            .dispatch(
                StateCondition::codec,
                Function.identity()
            )
    }

    object True : StateCondition {
        override val codec: MapCodec<out StateCondition> = MapCodec.unit(True)

        override fun check(controller: StateGraphController): Boolean = true
    }

    object False : StateCondition {
        override val codec: MapCodec<out StateCondition> = MapCodec.unit(False)

        override fun check(controller: StateGraphController): Boolean = false
    }

    class Reverse(
        val condition: StateCondition
    ): StateCondition {

        override val codec: MapCodec<out StateCondition> = CODEC

        override fun check(controller: StateGraphController): Boolean = !condition.check(controller)

        companion object {
            val CODEC = RecordCodecBuilder.mapCodec {
                it.group(
                    StateCondition.CODEC.fieldOf("condition").forGetter(Reverse::condition)
                ).apply(it, ::Reverse)
            }
        }
    }

    class Any(
        val conditions: List<StateCondition>
    ): StateCondition {
        override val codec: MapCodec<out StateCondition> = CODEC

        override fun check(controller: StateGraphController): Boolean = conditions.any { it.check(controller) }

        companion object {
            val CODEC = RecordCodecBuilder.mapCodec {
                it.group(
                    StateCondition.CODEC.listOf().fieldOf("conditions").forGetter(Any::conditions)
                ).apply(it, ::Any)
            }
        }
    }

    class All(
        val conditions: List<StateCondition>
    ) : StateCondition {
        override val codec: MapCodec<out StateCondition> = CODEC

        override fun check(controller: StateGraphController): Boolean =
            conditions.all { it.check(controller) }

        companion object {
            val CODEC = RecordCodecBuilder.mapCodec {
                it.group(
                    StateCondition.CODEC.listOf().fieldOf("conditions").forGetter(All::conditions)
                ).apply(it, ::All)
            }
        }
    }

}

// 逻辑非
operator fun StateCondition.not() =
    Reverse(this)

// 逻辑或
infix fun StateCondition.or(other: StateCondition) =
    StateCondition.Any(listOf(this, other))

// 逻辑与
infix fun StateCondition.and(other: StateCondition) =
    StateCondition.All(listOf(this, other))

