package cn.solarmoon.spark_core.gas

class AbilitySpecType<A: Ability, S: AbilitySpec<A>>(
    val abilityType: AbilityType<A>,
    private val provider: (AbilitySpecType<A, S>, AbilityType<A>, AbilitySystemComponent) -> S
) {

    fun create(asc: AbilitySystemComponent): S {
        return provider(this, abilityType, asc)
    }

}