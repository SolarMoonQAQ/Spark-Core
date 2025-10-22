package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.sync.EndAbilityLocalPayload
import cn.solarmoon.spark_core.gas.sync.TryActivateAbilityLocalPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor

class AbilitySystemComponent(
    val owner: AbilityHost,
    val level: Level
) {

    private var nextHandleId = 1
    private val abilitySpecs = mutableMapOf<AbilityHandle, AbilitySpec<*>>()
    private val listeners = mutableMapOf<GameplayTag, MutableMap<AbilitySpec<*>, MutableList<(AbilityEvent) -> Unit>>>()

    /**
     * #### 授予技能
     */
    fun giveAbility(spec: AbilitySpec<*>) {
        val handle = AbilityHandle(nextHandleId++)
        spec.initialize(this, handle)
        if (!level.isClientSide) {
            abilitySpecs[handle] = spec
            owner.syncGiveAbility(spec)
        }
    }

    /**
     * #### 移除技能
     */
    fun clearAbility(handle: AbilityHandle) {
        if (!level.isClientSide) {
            abilitySpecs.remove(handle)?.endAll()
            owner.syncClearAbility(handle)
        }
    }

    /**
     * #### 激活技能
     */
    fun tryActivateAbility(handle: AbilityHandle, context: ActivationContext): Boolean {
        if (!level.isClientSide) {
            if (abilitySpecs[handle]?.tryActivate(context) == true) {
                owner.syncTryActivateAbility(handle, context)
                return true
            }
        }
        return false
    }

    /**
     * #### 本地激活技能
     * 此方法仅能用于本地客户端的玩家激活技能，会立刻在本地尝试启用技能（预测），但同时会向服务端请求技能激活，如果服务端拒绝会结束客户端预测，反之正常广播给其它玩家
     */
    fun tryActivateAbilityLocal(handle: AbilityHandle, context: ActivationContext): Boolean {
        if (FMLEnvironment.dist.isClient) {
            if (abilitySpecs[handle]?.tryActivate(context) == true) {
                PacketDistributor.sendToServer(TryActivateAbilityLocalPayload(handle, context))
                return true
            }
        }
        return false
    }

    /**
     * #### 强制打断技能
     */
    fun cancelAbility(handle: AbilityHandle) {
        if (!level.isClientSide) {
            abilitySpecs[handle]?.cancelAll()
            owner.syncCancelAbility(handle)
        }
    }

    /**
     * #### 结束技能
     */
    fun endAbility(handle: AbilityHandle) {
        if (!level.isClientSide) {
            abilitySpecs[handle]?.endAll()
            owner.syncEndAbility(handle)
        }
    }

    fun endAllAbilities() {
        if (!level.isClientSide) {
            abilitySpecs.values.forEach { it.endAll() }
            owner.syncEndAllAbilities()
        }
    }

    fun endAbilityLocal(handle: AbilityHandle) {
        if (FMLEnvironment.dist.isClient) {
            abilitySpecs[handle]?.endAll()
            PacketDistributor.sendToServer(EndAbilityLocalPayload(handle))
        }
    }

    fun registerListener(tag: GameplayTag, spec: AbilitySpec<*>, callback: (AbilityEvent) -> Unit) {
        listeners.computeIfAbsent(tag) { mutableMapOf() }
            .computeIfAbsent(spec) { mutableListOf() }
            .add(callback)
    }

    fun unregisterListener(tag: GameplayTag, spec: AbilitySpec<*>) {
        listeners[tag]?.remove(spec)
    }

    fun handleGameplayEvent(event: AbilityEvent) {
        listeners[event.tag]?.forEach { (spec, callbacks) ->
            callbacks.forEach { it(event) }
        }
    }

    fun tick() {
        abilitySpecs.values.forEach { it.tick() }
    }

    // 默认查找器
    val allAbilitySpecs get() = abilitySpecs.toMap()

    val activeAbilitySpecs get() = abilitySpecs.filter { it.value.isActive }

    fun findSpecFromHandle(handle: AbilityHandle): AbilitySpec<*>? {
        return abilitySpecs[handle]
    }

    fun findSpecFromLocation(location: ResourceLocation): AbilitySpec<*>? {
        return abilitySpecs.values.firstOrNull { it.abilityType.registryKey == location }
    }

    // 仅同步用
    fun onRepGiveAbility(spec: AbilitySpec<*>) {
        abilitySpecs[spec.handle] = spec
    }

    fun onRepTryActivateAbility(handle: AbilityHandle, context: ActivationContext) {
        abilitySpecs[handle]?.apply {
            tryActivate(context)
        }
    }

    fun onRepEndAllAbilities() {
        abilitySpecs.values.forEach { it.endAll() }
    }

}