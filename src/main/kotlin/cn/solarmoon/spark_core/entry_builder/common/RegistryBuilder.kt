package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegistryBuilder as NeoForgeRegistryBuilder
import kotlin.reflect.KClass

class RegistryBuilder<T: Any>(private val modId: String, private val modBus: IEventBus) { // T: Any for KClass

    private var internalNeoForgeBuilder: NeoForgeRegistryBuilder<T>? = null
    private var valueType: KClass<*>? = null

    fun id(id: String) = apply {
        internalNeoForgeBuilder = NeoForgeRegistryBuilder(ResourceKey.createRegistryKey<T>(ResourceLocation.fromNamespaceAndPath(modId, id)))
    }

    fun id(namespace: String, path: String) = apply {
        internalNeoForgeBuilder = NeoForgeRegistryBuilder(ResourceKey.createRegistryKey<T>(ResourceLocation.fromNamespaceAndPath(namespace, path)))
    }

    fun valueType(type: KClass<*>) = apply {
        this.valueType = type
    }

    fun build(builderConfigurator :(NeoForgeRegistryBuilder<T>) -> Registry<T>): Registry<T> {
        val currentNeoForgeBuilder = internalNeoForgeBuilder ?: throw IllegalStateException("RegistryBuilder id must be set before building.")

        // 创建并注册静态注册表（参与 NeoForge 原生同步）
        val staticReg = builderConfigurator.invoke(currentNeoForgeBuilder)
        modBus.addListener { event: NewRegistryEvent -> register(staticReg, event) }

        // 返回静态注册表。对于需要动态能力的场景，请显式使用 VirtualRegistry 或专用实现。
        return staticReg
    }

    fun register(registry: Registry<T>, event: NewRegistryEvent) {
        event.register(registry)
    }

}
