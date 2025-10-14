package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.CommonRegisterBuilder
import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.ItemCapability
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class ItemBuilder<I : Item>(
    deferredRegister: DeferredRegister<Item>,
    private val bus: IEventBus
) : CommonRegisterBuilder<Item, I>(deferredRegister) {

    private val caps = mutableListOf<Pair<ItemCapability<*, *>, ICapabilityProvider<ItemStack, *, *>>>()

    fun capabilities(block: CapabilityDsl.() -> Unit) = apply {
        CapabilityDsl().apply(block)
    }

    inner class CapabilityDsl {
        infix fun <C> ItemCapability<*, C>.with(provider: ICapabilityProvider<ItemStack, C, *>) {
            caps += this to provider
        }
    }

    override fun build(): DeferredHolder<Item, I> {
        val reg = super.build()
        if (caps.isNotEmpty()) {
            bus.addListener { e: RegisterCapabilitiesEvent ->
                for ((cap, provider) in caps) {
                    e.registerItem(
                        cap as ItemCapability<Any, Any?>,
                        provider as ICapabilityProvider<ItemStack, Any?, Any>,
                        reg.get()
                    )
                }
            }
        }
        return reg
    }

}

