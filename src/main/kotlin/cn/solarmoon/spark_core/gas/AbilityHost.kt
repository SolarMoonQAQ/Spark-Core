package cn.solarmoon.spark_core.gas

interface AbilityHost {

    var abilitySystemComponent: AbilitySystemComponent

    fun syncGiveAbility(spec: AbilitySpec<*>)

    fun syncClearAbility(handle: AbilityHandle)

    fun syncTryActivateAbility(handle: AbilityHandle, context: ActivationContext)

    fun syncCancelAbility(handle: AbilityHandle)

    fun syncEndAbility(handle: AbilityHandle)

    fun syncEndAllAbilities()

}