package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
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

    fun id(namespace: String, path: String) = apply {
        builder = RegistryBuilder(ResourceKey.createRegistryKey<T>(ResourceLocation.fromNamespaceAndPath(namespace, path)))
    }

    // 静态注册表映射，避免重复包装
    companion object {
        private val staticRegistryMap = mutableMapOf<ResourceKey<*>, DynamicAwareRegistry<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T> getOrCreateDynamicRegistry(staticRegistry: Registry<T>): DynamicAwareRegistry<T> {
            val key = staticRegistry.key()
            return staticRegistryMap.getOrPut(key) {
                DynamicAwareRegistry(staticRegistry)
            } as DynamicAwareRegistry<T>
        }
    }

    fun build(builder :(RegistryBuilder<T>) -> Registry<T>): Registry<T> {
        // 创建静态注册表
        val staticReg = builder.invoke(this.builder!!)
        modBus.addListener { event: NewRegistryEvent -> register(staticReg, event) }

        // 包装为动态注册表
        return getOrCreateDynamicRegistry(staticReg)
    }

    fun register(registry: Registry<T>, event: NewRegistryEvent) {
        event.register(registry)
    }

}