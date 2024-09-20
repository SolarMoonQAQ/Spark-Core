package cn.solarmoon.spark_core.api.entry_builder.common

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.SimpleParticleType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class ParticleBuilder<P: ParticleType<*>>(private val particleDeferredRegister: DeferredRegister<ParticleType<*>>) {

    private var id = ""
    private var particleSupplier: Supplier<P>? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(particleSupplier: Supplier<P>) = apply { this.particleSupplier = particleSupplier }

    fun build(): DeferredHolder<ParticleType<*>, P> {
        return particleDeferredRegister.register(id, particleSupplier!!)
    }

}