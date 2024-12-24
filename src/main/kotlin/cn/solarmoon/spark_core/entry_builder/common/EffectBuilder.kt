package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.world.effect.MobEffect
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class EffectBuilder(private val effectDeferredRegister: DeferredRegister<MobEffect>) {//

    private var id = ""
    private var effectSupplier: Supplier<MobEffect>? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(effectSupplier: Supplier<MobEffect>) = apply { this.effectSupplier = effectSupplier }

    fun build(): DeferredHolder<MobEffect, MobEffect> {
        return effectDeferredRegister.register(id, effectSupplier!!)
    }

}