package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegistryBuilder as NeoForgeRegistryBuilder // Alias to avoid confusion
import kotlin.reflect.KClass

class RegistryBuilder<T: Any>(private val modId: String, private val modBus: IEventBus) { // T: Any for KClass

    private var internalNeoForgeBuilder: NeoForgeRegistryBuilder<T>? = null
    private var valueType: KClass<out T>? = null

    fun id(id: String) = apply {
        internalNeoForgeBuilder = NeoForgeRegistryBuilder(ResourceKey.createRegistryKey<T>(ResourceLocation.fromNamespaceAndPath(modId, id)))
    }

    fun id(namespace: String, path: String) = apply {
        internalNeoForgeBuilder = NeoForgeRegistryBuilder(ResourceKey.createRegistryKey<T>(ResourceLocation.fromNamespaceAndPath(namespace, path)))
    }

    fun valueType(type: KClass<out T>) = apply {
        this.valueType = type
    }

    // 静态注册表映射，避免重复包装
    companion object {
        private val staticRegistryMap = mutableMapOf<ResourceKey<*>, DynamicAwareRegistry<*>>()

        /**
         * 获取或创建动态注册表
         *
         * @param staticRegistry 静态注册表
         * @param valueType The KClass of the elements stored in the registry.
         * @return 动态感知注册表
         */
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getOrCreateDynamicRegistry(staticRegistry: Registry<T>, valueType: KClass<T>): DynamicAwareRegistry<T> {
            // 获取注册表键值
            val key = staticRegistry.key()
            // 从静态注册表映射中获取或创建新的动态感知注册表
            return staticRegistryMap.getOrPut(key) {
                DynamicAwareRegistry(staticRegistry, valueType)
            } as DynamicAwareRegistry<T>
        }
    }

    fun build(builderConfigurator :(NeoForgeRegistryBuilder<T>) -> Registry<T>): Registry<T> {
        val currentNeoForgeBuilder = internalNeoForgeBuilder ?: throw IllegalStateException("RegistryBuilder id must be set before building.")
        val currentValueType = valueType ?: throw IllegalStateException("RegistryBuilder valueType must be set before building for DynamicAwareRegistry.")
        
        // 创建静态注册表
        val staticReg = builderConfigurator.invoke(currentNeoForgeBuilder)
        modBus.addListener { event: NewRegistryEvent -> register(staticReg, event) } // Assuming register is a method in this class or accessible

        // 包装为动态注册表
        @Suppress("UNCHECKED_CAST")
        return getOrCreateDynamicRegistry(staticReg, currentValueType as KClass<T>)
    }

    fun register(registry: Registry<T>, event: NewRegistryEvent) {
        event.register(registry)
    }

}