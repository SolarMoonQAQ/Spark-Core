package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import cn.solarmoon.spark_core.gas.Ability
import cn.solarmoon.spark_core.gas.AbilitySpec
import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.gas.ActivationContext
import cn.solarmoon.spark_core.gas.InstancingPolicy
import com.mojang.serialization.Codec
import com.oracle.truffle.`object`.enterprise.a
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

//class AbilityTypeBuilder<A: Ability>(
//    abilityTypeDeferredRegister: DeferredRegister<AbilityType<*>>
//): RegisterBuilder<AbilityType<*>, AbilityType<A>>(abilityTypeDeferredRegister) {
//
//    var instancingPolicy = InstancingPolicy.INSTANCED_PER_ACTOR
//    lateinit var factory: (AbilitySpec<A>, ActivationContext) -> A
//    lateinit var codec: ((AbilitySpec<A>, ActivationContext) -> A) -> Codec<AbilityType<A>>
//
//    override fun validate() {
//        super.validate()
//        if (!this::factory.isInitialized) {
//            throw IllegalStateException("未给 ${javaClass.simpleName} 指定构造函数!")
//        }
//    }
//
//    override fun build(): DeferredHolder<AbilityType<*>, AbilityType<A>> {
//        val reg = deferredRegister.register(id, Supplier { AbilityType(instancingPolicy, factory) })
//        return reg
//    }
//
//}