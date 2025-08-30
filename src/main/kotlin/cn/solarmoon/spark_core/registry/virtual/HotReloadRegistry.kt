package cn.solarmoon.spark_core.registry.virtual

import net.minecraft.core.Holder
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

interface HotReloadRegistry<T : Any> {
    fun key(): ResourceKey<out Registry<T>>

    fun register(key: ResourceKey<T>, value: T, info: RegistrationInfo): Holder.Reference<T>

    fun registerDynamic(
        name: ResourceLocation,
        value: T,
        moduleId: String,
        replace: Boolean = true,
        triggerCallback: Boolean = true
    ): T

    fun unregisterDynamic(key: ResourceKey<T>): Boolean
    fun unregisterDynamic(name: ResourceLocation): Boolean

    operator fun get(key: ResourceKey<T>): T?
    fun containsKey(name: ResourceLocation): Boolean
    fun entrySet(): Set<Map.Entry<ResourceKey<T>, T>>

    fun clearDynamic()
    fun updateDynamicId(key: ResourceLocation, newId: Int): Boolean
}
