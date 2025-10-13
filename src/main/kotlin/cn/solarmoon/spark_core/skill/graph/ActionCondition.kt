package cn.solarmoon.spark_core.skill.graph

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.graph.ActionCondition.Reverse
import com.mojang.datafixers.util.Unit
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.function.Function

interface ActionCondition {

    val codec: MapCodec<out ActionCondition>

    fun check(controller: ActionController): Boolean

    companion object {
        val CODEC = SparkRegistries.ACTION_CONDITION_CODEC.byNameCodec()
            .dispatch(
                ActionCondition::codec,
                Function.identity()
            )
    }

    object True : ActionCondition {
        override val codec: MapCodec<out ActionCondition> = MapCodec.unit(True)

        override fun check(controller: ActionController): Boolean = true
    }

    object False : ActionCondition {
        override val codec: MapCodec<out ActionCondition> = MapCodec.unit(False)

        override fun check(controller: ActionController): Boolean = false
    }

    class Reverse(
        val condition: ActionCondition
    ): ActionCondition {

        override val codec: MapCodec<out ActionCondition> = CODEC

        override fun check(controller: ActionController): Boolean = !condition.check(controller)

        companion object {
            val CODEC = RecordCodecBuilder.mapCodec {
                it.group(
                    ActionCondition.CODEC.fieldOf("condition").forGetter(Reverse::condition)
                ).apply(it, ::Reverse)
            }
        }
    }

    class Any(
        val conditions: List<ActionCondition>
    ): ActionCondition {
        override val codec: MapCodec<out ActionCondition> = CODEC

        override fun check(controller: ActionController): Boolean = conditions.any { it.check(controller) }

        companion object {
            val CODEC = RecordCodecBuilder.mapCodec {
                it.group(
                    ActionCondition.CODEC.listOf().fieldOf("conditions").forGetter(Any::conditions)
                ).apply(it, ::Any)
            }
        }
    }

    class All(
        val conditions: List<ActionCondition>
    ) : ActionCondition {
        override val codec: MapCodec<out ActionCondition> = CODEC

        override fun check(controller: ActionController): Boolean =
            conditions.all { it.check(controller) }

        companion object {
            val CODEC = RecordCodecBuilder.mapCodec {
                it.group(
                    ActionCondition.CODEC.listOf().fieldOf("conditions").forGetter(All::conditions)
                ).apply(it, ::All)
            }
        }
    }

}

// 逻辑非
operator fun ActionCondition.not() =
    ActionCondition.Reverse(this)

// 逻辑或
infix fun ActionCondition.or(other: ActionCondition) =
    ActionCondition.Any(listOf(this, other))

// 逻辑与
infix fun ActionCondition.and(other: ActionCondition) =
    ActionCondition.All(listOf(this, other))

