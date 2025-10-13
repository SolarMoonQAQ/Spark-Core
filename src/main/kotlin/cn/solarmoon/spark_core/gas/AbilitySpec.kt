package cn.solarmoon.spark_core.gas

/**
 * ### 技能容器
 * 包含对技能实例的引用，用于调配技能实例
 *
 * 完全可以类比如MOBA游戏的技能槽的功能
 */
open class AbilitySpec<A: Ability>(
    val type: AbilitySpecType<A, *>,
    val abilityType: AbilityType<A>,
    val asc: AbilitySystemComponent,
) {

    var handle: AbilityHandle = AbilityHandle(-1)
        internal set

    val activeAbilities = mutableListOf<Ability>()

    fun tryActivate(context: ActivationContext) {
        val ability = when (abilityType.instancingPolicy) {
            InstancingPolicy.INSTANCED_PER_ACTOR -> {
                activeAbilities.firstOrNull() ?: abilityType.create(this, context)
            }
            InstancingPolicy.INSTANCED_PER_EXECUTION -> {
                abilityType.create(this, context)
            }
        }

        val result = ability.canActivate()
        when(result.success) {
            true -> {
                ability.emit(AbilityEvent(gameplayTag("Ability") attach "Success", handle, result.tags))
                activeAbilities += ability
                ability.activate()
            }
            false -> {
                ability.emit(AbilityEvent(gameplayTag("Ability") attach "Failure", handle, result.tags))
                return
            }
        }
    }

    open fun tick() {
        activeAbilities.forEach { it.tasks.forEach { it.tick() } }
    }

    fun endAll() {
        activeAbilities.forEach { it.end() }
        activeAbilities.clear()
    }

}




