package cn.solarmoon.spark_core.entry_builder.common

import com.mojang.serialization.MapCodec
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class ParticleBuilder<O: ParticleOptions>(private val particleDeferredRegister: DeferredRegister<ParticleType<*>>) {

    private var id = ""
    private var particleSupplier: Supplier<ParticleType<O>>? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(particleSupplier: Supplier<ParticleType<O>>) = apply { this.particleSupplier = particleSupplier }

    fun bound(ignoreDistance: Boolean, codec: (ParticleType<O>) -> MapCodec<O>, streamCodec: (ParticleType<O>) -> StreamCodec<in RegistryFriendlyByteBuf, O>) = apply {
        particleSupplier = Supplier {
            object: ParticleType<O>(ignoreDistance) {
                override fun codec(): MapCodec<O> = codec(this)

                override fun streamCodec(): StreamCodec<in RegistryFriendlyByteBuf, O> = streamCodec(this)
            }
         }
    }

    fun build(): DeferredHolder<ParticleType<*>, ParticleType<O>> {
        return particleDeferredRegister.register(id, particleSupplier!!)
    }

}