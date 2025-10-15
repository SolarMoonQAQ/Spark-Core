package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.SparkCore

/**
 * ### 技能容器
 * 包含对技能实例的引用，用于调配技能实例，可以储存一些动态参数，如技能等级/蓝耗等
 */
class AbilitySpec<A: Ability>(
    val abilityType: AbilityType<A>,
) {

    lateinit var asc: AbilitySystemComponent
        private set
    var handle: AbilityHandle = AbilityHandle(-1)
        private set

    val dynamicTags = GameplayTagContainer()
    val activeAbilities = mutableListOf<Ability>()

    val isActive get() = activeAbilities.isNotEmpty()

    fun initialize(asc: AbilitySystemComponent, handle: AbilityHandle) {
        this.asc = asc
        this.handle = handle
    }

    fun tryActivate(context: ActivationContext): Boolean {
        if (!::asc.isInitialized) {
            SparkCore.LOGGER.info("技能容器(${AbilityTypeManager.getKey(abilityType)})还未授予到技能持有者中，无法激活技能")
            return false
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
        when(result) {
            true -> {
                activeAbilities += ability
                ability.activate(this, context)
            }
            false -> {
            }
        }
        return result
    }

    fun tick() {
        activeAbilities.forEach { it.tasks.forEach { it.tick() } }
    }

    fun endAbility(ability: Ability, wasCancelled: Boolean) {
        if (!activeAbilities.contains(ability)) return
        ability.end(this, wasCancelled)
        // 结束所有task
        ability.tasks.toList().forEach { it.end(ownerFinished = true) }
        ability.tasks.clear()
        activeAbilities.remove(ability)
    }

    fun cancelAll() {
        activeAbilities.toList().forEach { endAbility(it, wasCancelled = true) }
    }

    fun endAll() {
        activeAbilities.toList().forEach { endAbility(it, wasCancelled = false) }
    }

    companion object {
        val STREAM_CODEC = AbilityType.STREAM_CODEC.map({ AbilitySpec(it) }, { it.abilityType })
    }

}




