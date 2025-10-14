package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.util.function.Function

/**
 * ### 技能容器
 * 包含对技能实例的引用，用于调配技能实例
 *
 * 同时，技能容器强制要求实现自身的网络解码，因为技能容器总是以服务器为权威下发
 *
 * 完全可以类比如MOBA游戏的技能槽的功能
 */
open class AbilitySpec<A: Ability>(
    val abilityType: AbilityType<A>,
) {

    lateinit var asc: AbilitySystemComponent
        private set
    var handle: AbilityHandle = AbilityHandle(-1)
        private set

    val activeAbilities = mutableListOf<Ability>()

    open val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out AbilitySpec<*>> = Companion.streamCodec

    fun initialize(asc: AbilitySystemComponent, handle: AbilityHandle) {
        this.asc = asc
        this.handle = handle
    }

    fun tryActivate(context: ActivationContext) {
        if (!::asc.isInitialized) {
            SparkCore.LOGGER.info("技能容器还未授予到技能持有者中，无法激活技能")
            return
        }

        val ability = when (abilityType.instancingPolicy) {
            InstancingPolicy.INSTANCED_PER_ACTOR -> {
                activeAbilities.firstOrNull() ?: abilityType.create()
            }
            InstancingPolicy.INSTANCED_PER_EXECUTION -> {
                abilityType.create()
            }
        }

        val result = ability.canActivate(this, context)
        when(result.success) {
            true -> {
                activeAbilities += ability
                ability.activate(this, context)
            }
            false -> {
                return
            }
        }
    }

    open fun tick() {
        activeAbilities.forEach { it.tasks.forEach { it.tick() } }
    }

    fun endAll() {
        activeAbilities.forEach { it.end(this) }
        activeAbilities.clear()
    }

    companion object {
        val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out AbilitySpec<*>> = StreamCodec.composite(
            AbilityType.STREAM_CODEC, AbilitySpec<*>::abilityType
        ) { AbilitySpec(it) }

        val STREAM_CODEC = ByteBufCodecs.registry(SparkRegistries.ABILITY_SPEC_STREAM_CODEC.key()).dispatch(
            AbilitySpec<*>::streamCodec,
            Function.identity()
        )
    }

}




