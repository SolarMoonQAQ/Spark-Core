package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.network.codec.ByteBufCodecs
import java.util.function.Function

class AbilityType<A: Ability>(
    val instancingPolicy: InstancingPolicy,
    private val provider: () -> A,
) {

    val registryKey get() = AbilityTypeManager.getKey(this) ?: throw IllegalStateException("技能类型尚未注册")

    var id = -1
        internal set

    // 此方法不可用于触发技能，技能只能从spec或asc控制
    fun create(): A {
        return provider.invoke()
    }

    interface Serializer {
        val codec: MapCodec<out Serializer>

        fun create(): AbilityType<*>

        companion object {
            val CODEC = SparkRegistries.ABILITY_TYPE_CODEC.byNameCodec()
                .dispatch(
                    Serializer::codec,
                    Function.identity()
                )
        }
    }

    companion object {
        val STREAM_CODEC = ByteBufCodecs.INT.map({ AbilityTypeManager.getAbilityType(it)!! }, { it.id })
    }

}