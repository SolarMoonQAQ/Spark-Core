package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.ItemCapability
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class ItemBuilder<I: Item>(private val itemDeferredRegister: DeferredRegister<Item>, private val bus: IEventBus) {//

    private var id: String = ""
    private var item: Supplier<I>? = null
    private var capP: MutableList<Pair<ItemCapability<*, *>, ICapabilityProvider<ItemStack, *, *>>> = mutableListOf()

    fun id(id: String) = apply { this.id = id }
    fun capability(cap: ItemCapability<*, *>, provider: ICapabilityProvider<ItemStack, *, *>) = apply { capP.add(Pair(cap, provider)) }
    fun bound(item: Supplier<I>) = apply { this.item = item }

    fun build(): DeferredHolder<Item, I> {
        val reg = itemDeferredRegister.register(id, item!!)
        if (!capP.isEmpty()) bus.addListener { e: RegisterCapabilitiesEvent -> registerCap(reg, e) }
        return reg
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerCap(reg: DeferredHolder<Item, I>, event: RegisterCapabilitiesEvent) {
        for (cap in capP) {
            event.registerItem(cap.first as ItemCapability<Any, Any?>, cap.second as ICapabilityProvider<ItemStack, Any?, Any>, reg.get())
        }
    }

}