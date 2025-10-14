package cn.solarmoon.spark_core.gas

interface AbilityTypeSerializer<A: Ability> {
    fun create(spec: AbilitySpec<A>, context: ActivationContext): AbilityType<A>
}
