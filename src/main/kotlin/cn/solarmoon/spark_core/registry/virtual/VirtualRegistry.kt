package cn.solarmoon.spark_core.registry.virtual

import cn.solarmoon.spark_core.registry.dynamic.DynamicIdManager
import net.minecraft.core.*
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
* VirtualRegistry：与普通/NeoForge 全局注册表解耦的内存注册表。
 * 仅由内部映射 + DynamicIdManager 支持，以实现稳定的 ID。
 */
class VirtualRegistry<T : Any>(
    private val registryKeyLocation: ResourceLocation,
): HotReloadRegistry<T> {

    var onDynamicRegister: ((key: ResourceKey<T>, value: T) -> Unit)? = null
    var onDynamicUnregister: ((key: ResourceKey<T>, value: T) -> Unit)? = null

    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val entries = ConcurrentHashMap<ResourceLocation, T>()
    private val keyToId = ConcurrentHashMap<ResourceLocation, Int>()
    private val idToKey = ConcurrentHashMap<Int, ResourceLocation>()
    private val valueToKey = ConcurrentHashMap<T, ResourceLocation>()

    private val moduleToResources = ConcurrentHashMap<String, MutableSet<ResourceLocation>>()
    private val resourceToModule = ConcurrentHashMap<ResourceLocation, String>()

    private val dynamicOwner: HolderOwner<T> = object : HolderOwner<T> {}

    override fun key(): ResourceKey<out Registry<T>> = ResourceKey.createRegistryKey(registryKeyLocation)

    override fun registerDynamic(
        name: ResourceLocation,
        value: T,
        moduleId: String,
        replace: Boolean,
        triggerCallback: Boolean
    ): T {
        lock.writeLock().withLock {
            val location = name
            if (entries.containsKey(name)) {
                // replace existing
                val prev = entries[name]
                if (prev != null && prev !== value) {
                    valueToKey.remove(prev)
                }
            }

            val baseId = 0 // virtual registry has no static ids; base provided by caller when allocating
            val registryName = key().location().toString()
            val id = DynamicIdManager.allocateId(registryName, location, baseId)

            entries[location] = value
            valueToKey[value] = location

            if (id >= 0) {
                keyToId[location] = id
                idToKey[id] = location
            }

            resourceToModule[location] = moduleId
            moduleToResources.computeIfAbsent(moduleId) { ConcurrentHashMap.newKeySet() }.add(location)

            if (triggerCallback) {
                onDynamicRegister?.invoke(ResourceKey.create(this.key(), location), value)
            }
            return value
        }
    }

    override fun register(key: ResourceKey<T>, value: T, info: RegistrationInfo): Holder.Reference<T> {
        lock.writeLock().withLock {
            val location = key.location()
            val registryName = key().location().toString()
            val id = DynamicIdManager.allocateId(registryName, location, 0)

            entries[location] = value
            valueToKey[value] = location
            if (id >= 0) {
                keyToId[location] = id
                idToKey[id] = location
            }

            val holder = Holder.Reference.createStandAlone(dynamicOwner, key)
            holder.value = value
            onDynamicRegister?.invoke(key, value)
            return holder
        }
    }

    override fun unregisterDynamic(location: ResourceLocation): Boolean {
        lock.writeLock().withLock {
            val value = entries.remove(location) ?: return false
            valueToKey.remove(value)
            keyToId.remove(location)?.let { idToKey.remove(it) }
            resourceToModule.remove(location)?.let { moduleId ->
                moduleToResources[moduleId]?.remove(location)
                if (moduleToResources[moduleId]?.isEmpty() == true) moduleToResources.remove(moduleId)
            }
            onDynamicUnregister?.invoke(ResourceKey.create(key(), location), value)
            return true
        }
    }

    override fun unregisterDynamic(key: ResourceKey<T>): Boolean = unregisterDynamic(key.location())

    operator fun get(name: ResourceLocation): T? = lock.readLock().withLock { entries[name] }

    override operator fun get(key: ResourceKey<T>): T? = get(key.location())

    override fun containsKey(name: ResourceLocation): Boolean = lock.readLock().withLock { entries.containsKey(name) }

    fun byId(id: Int): T? {
        lock.readLock().withLock {
            val name = idToKey[id] ?: return null
            return entries[name]
        }
    }

    fun getId(value: T?): Int {
        if (value == null) return -1
        lock.readLock().withLock {
            // avoid value.hashCode recursion by linear scan
            for ((v, k) in valueToKey.entries) {
                if (v === value) {
                    return keyToId[k] ?: -1
                }
            }
        }
        return -1
    }

    fun getId(name: ResourceLocation): Int {
        lock.readLock().withLock {
            return keyToId[name] ?: -1
        }
    }

    fun getKey(value: T): ResourceLocation? {
        lock.readLock().withLock {
            for ((v, k) in valueToKey.entries) if (v === value) return k
        }
        return null
    }

    fun getResourceKey(value: T): Optional<ResourceKey<T>> {
        val name = getKey(value) ?: return Optional.empty()
        return Optional.of(ResourceKey.create(this.key(), name))
    }

    override fun entrySet(): Set<Map.Entry<ResourceKey<T>, T>> {
        val set = HashSet<Map.Entry<ResourceKey<T>, T>>()
        lock.readLock().withLock {
            entries.forEach { (name, value) ->
                val rk = ResourceKey.create(this@VirtualRegistry.key(), name)
                set.add(object : Map.Entry<ResourceKey<T>, T> {
                    override val key: ResourceKey<T> = rk
                    override val value: T = value
                })
            }
        }
        return set
    }

    override fun clearDynamic() {
        lock.writeLock().withLock {
            entries.clear(); keyToId.clear(); idToKey.clear(); valueToKey.clear()
            moduleToResources.clear(); resourceToModule.clear()
        }
    }

    override fun updateDynamicId(key: ResourceLocation, newId: Int): Boolean {
        if (newId < 0) return false
        lock.writeLock().withLock {
            if (!entries.containsKey(key)) return false
            keyToId[key]?.let { idToKey.remove(it) }
            keyToId[key] = newId
            idToKey[newId] = key
            return true
        }
    }

    // --- Compatibility helpers for existing call sites ---
    fun size(): Int = lock.readLock().withLock { entries.size }
    fun isEmpty(): Boolean = size() == 0
    fun keySet(): Set<ResourceLocation> = lock.readLock().withLock { entries.keys.toSet() }
    fun getDynamicEntries(): Map<ResourceLocation, T> = lock.readLock().withLock { entries.toMap() }
}
