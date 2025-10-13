package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.BlockCapability
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

class BlockEntityTypeBuilder<B : BlockEntity>(
    deferredRegister: DeferredRegister<BlockEntityType<*>>,
    private val bus: IEventBus
) : RegisterBuilder<BlockEntityType<*>, BlockEntityType<B>>(deferredRegister) {

    private val caps = mutableListOf<Pair<BlockCapability<*, *>, ICapabilityProvider<B, *, *>>>()

    fun capabilities(block: CapabilityDsl.() -> Unit) = apply {
        CapabilityDsl().apply(block)
    }

    inner class CapabilityDsl {
        infix fun BlockCapability<*, *>.with(provider: ICapabilityProvider<B, *, *>) {
            caps += this to provider
        }
    }

    override fun build(): DeferredHolder<BlockEntityType<*>, BlockEntityType<B>> {
        val reg = super.build()
        if (caps.isNotEmpty()) {
            bus.addListener { e: RegisterCapabilitiesEvent ->
                for ((cap, provider) in caps) {
                    e.registerBlockEntity(
                        cap as BlockCapability<Any, Any?>,
                        reg.get(),
                        provider as ICapabilityProvider<B, Any?, Any>
                    )
                }
            }
        }
        return reg
    }

}


