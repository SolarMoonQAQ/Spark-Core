package cn.solarmoon.spark_core.gas

class AbilityType<A: Ability>(
    val instancingPolicy: InstancingPolicy = InstancingPolicy.INSTANCED_PER_ACTOR,
    private val provider: (AbilitySpec<A>, ActivationContext) -> A,
) {

    fun create(spec: AbilitySpec<A>, context: ActivationContext): A {
        return provider.invoke(spec, context)
    }

}