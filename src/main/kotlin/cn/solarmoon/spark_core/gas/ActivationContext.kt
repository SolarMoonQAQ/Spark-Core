package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.util.function.Function

/**
 * ### 技能释放上下文
 * 一个技能的释放有时会需要一个动态参数，比如在客户端，玩家对着一个方块坐标释放一道闪电，此时需要给技能动态的传入方块坐标，并且这个参数还需要能够告知给服务器，
 * 此时就需要这个运行时上下文。
 *
 * 此上下文强制要求实现一个网络解码，以方便进行数据同步，
 *
 * 注意，需要区分动态和静态配置，此处上下文仅用于技能构造时的可同步配置，对于技能的静态配置（如固定蓝耗/冷却/伤害等）请直接在Ability的构造函数中传入，并在注册时指定，对于动态配置，请自行同步
 */
interface ActivationContext {

    val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out ActivationContext>

    companion object {
        val STREAM_CODEC = ByteBufCodecs.registry(SparkRegistries.ACTIVATION_CONTEXT_STREAM_CODEC.key()).dispatch(
            ActivationContext::streamCodec,
            Function.identity()
        )
    }

    object Empty: ActivationContext {
        override val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out ActivationContext> = StreamCodec.unit(Empty)
    }

}