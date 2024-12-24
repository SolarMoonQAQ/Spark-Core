package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegistryBuilder

class RegistryBuilder<T>(private val modId: String, private val modBus: IEventBus) {

    private var builder: RegistryBuilder<T>? = null

    fun id(id: String) = apply {
        builder = RegistryBuilder(ResourceKey.createRegistryKey<T>(ResourceLocation.fromNamespaceAndPath(modId, id)))
    }

    fun build(builder :(RegistryBuilder<T>) -> Registry<T>): Registry<T> {
        val reg = builder.invoke(this.builder!!)
        modBus.addListener { event: NewRegistryEvent -> register(reg, event) }
        return reg
    }

    fun register(registry: Registry<T>, event: NewRegistryEvent) {
        event.register(registry)
    }

}