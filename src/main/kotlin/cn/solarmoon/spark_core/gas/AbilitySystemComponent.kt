package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.gas.sync.ActivateAbilityLocalPayload
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor

open class AbilitySystemComponent(
    val owner: AbilityHost,
    val level: Level
) {

    private var nextHandleId = 1
    private val abilitySpecs = mutableMapOf<AbilityHandle, AbilitySpec<*>>()

    val abilitySpecsByAbilityType get() = abilitySpecs.values.groupBy { it.abilityType.registryKey }

    val allAbilitySpecs get() = abilitySpecs.toMap()

    /**
     * #### 授予技能
     * 实际上可以当作是将技能注册到当前持有者身上，因此以服务端为准下发
     */
    fun grantAbility(spec: AbilitySpec<*>) {
        val handle = AbilityHandle(nextHandleId++)
        spec.initialize(this, handle)
        if (!level.isClientSide) {
            abilitySpecs[handle] = spec
            owner.syncGrantAbilitySpec(spec)
        }
    }

    /**
     * #### 激活技能
     * 技能的激活总是需要以服务端为权威，此方法要求在服务端调用，会激活技能并通知给客户端激活
     */
    fun activateAbility(handle: AbilityHandle, context: ActivationContext) {
        if (!level.isClientSide) {
            abilitySpecs[handle]?.tryActivate(context)
            owner.syncActivateAbility(handle, context)
        }
    }

    /**
     * #### 本地激活技能
     * 此方法仅能用于本地客户端的玩家激活技能，会立刻在本地尝试启用技能（预测），但同时会向服务端请求技能激活，如果服务端拒绝会结束客户端预测，反之正常广播给其它玩家
     */
    fun activateAbilityLocal(handle: AbilityHandle, context: ActivationContext) {
        abilitySpecs[handle]?.apply {
            tryActivate(context)
            PacketDistributor.sendToServer(ActivateAbilityLocalPayload(handle, context))
        }
    }

    fun emitEvent(event: AbilityEvent) {
        abilitySpecs.values.forEach { spec ->
            spec.activeAbilities.forEach { ability ->
                ability.onEvent(spec, event)
            }
        }
    }

    fun getSpec(handle: AbilityHandle) = abilitySpecs[handle]

    fun endAbility(handle: AbilityHandle) {
        abilitySpecs[handle]?.endAll()
    }

    fun tick() {
        abilitySpecs.values.forEach { it.tick() }
    }

    // 仅同步用
    internal fun putSpec(spec: AbilitySpec<*>) {
        abilitySpecs[spec.handle] = spec
    }

    internal fun active(handle: AbilityHandle, context: ActivationContext) {
        abilitySpecs[handle]?.tryActivate(context)
    }

}

