package cn.solarmoon.spark_core.registry.dynamic

import cn.solarmoon.spark_core.SparkCore
import com.mojang.datafixers.util.Pair
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderOwner
import net.minecraft.core.HolderSet
import net.minecraft.core.IdMap
import net.minecraft.core.Registry
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.WritableRegistry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.neoforged.neoforge.registries.IRegistryExtension
import net.neoforged.neoforge.registries.callback.RegistryCallback
import net.neoforged.neoforge.registries.datamaps.DataMapType
import java.util.Collections
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Stream

/**
 * 动态感知注册表
 *
 * 包装静态 Registry<T>，同时维护一个 ConcurrentHashMap 用于动态条目
 * 实现 Registry<T> 接口的关键查询方法，并提供 registerDynamic/unregisterDynamic 方法
 *
 * @param staticRegistry 被包装的静态注册表
 */
class DynamicAwareRegistry<T>(private val staticRegistry: Registry<T>) : Registry<T> {

    // 动态条目存储
    private val dynamicEntries = ConcurrentHashMap<ResourceLocation, T>()
    private val dynamicKeyToId = ConcurrentHashMap<ResourceLocation, Int>()
    private val dynamicIdToKey = ConcurrentHashMap<Int, ResourceLocation>()
    private val dynamicValueToKey = ConcurrentHashMap<T, ResourceLocation>()

    // 线程安全锁
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    // 动态ID生成器，从最大静态ID开始递增
    private val nextDynamicId = AtomicInteger(findMaxStaticId() + 1)

    // 查找静态注册表中的最大ID
    private fun findMaxStaticId(): Int {
        var maxId = -1
        for (i in 0 until staticRegistry.size()) {
            val id = i
            staticRegistry.getHolder(id).ifPresent {
                if (id > maxId) maxId = id
            }
        }
        return maxId
    }

    /**
     * 动态注册一个条目
     *
     * @param key 资源位置
     * @param value 要注册的值
     * @return 注册的值
     */
    fun registerDynamic(key: ResourceLocation, value: T): T {
        lock.writeLock().lock()
        try {
            // 检查是否已存在于静态注册表
            if (staticRegistry.containsKey(key)) {
                SparkCore.LOGGER.info("Cannot register dynamic entry with key $key: already exists in static registry")
                return value
            }

            // 检查是否已存在于动态注册表
            if (dynamicEntries.containsKey(key)) {
                SparkCore.LOGGER.info("Cannot register dynamic entry with key $key: already exists in dynamic registry")
                return value
            }

            // 分配ID并存储
            val id = nextDynamicId.getAndIncrement()
            dynamicEntries[key] = value
            dynamicKeyToId[key] = id
            dynamicIdToKey[id] = key
            dynamicValueToKey[value] = key

            return value
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * 动态注册一个条目（使用 ResourceKey）
     *
     * @param key 资源键
     * @param value 要注册的值
     * @return 注册的值
     */
    fun registerDynamic(key: ResourceKey<T>, value: T): T {
        return registerDynamic(key.location(), value)
    }

    /**
     * 取消注册一个动态条目
     *
     * @param key 资源位置
     * @return 是否成功取消注册
     */
    fun unregisterDynamic(key: ResourceLocation): Boolean {
        lock.writeLock().lock()
        try {
            // 只能取消注册动态条目
            if (!dynamicEntries.containsKey(key)) {
                return false
            }

            val value = dynamicEntries[key]
            val id = dynamicKeyToId[key]

            dynamicEntries.remove(key)
            dynamicKeyToId.remove(key)
            if (id != null) {
                dynamicIdToKey.remove(id)
            }
            if (value != null) {
                dynamicValueToKey.remove(value)
            }

            return true
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * 取消注册一个动态条目（使用 ResourceKey）
     *
     * @param key 资源键
     * @return 是否成功取消注册
     */
    fun unregisterDynamic(key: ResourceKey<T>): Boolean {
        return unregisterDynamic(key.location())
    }

    /**
     * 清除所有动态条目
     */
    fun clearDynamic() {
        lock.writeLock().lock()
        try {
            dynamicEntries.clear()
            dynamicKeyToId.clear()
            dynamicIdToKey.clear()
            dynamicValueToKey.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * 获取所有动态条目的资源位置
     */
    fun getDynamicKeys(): Set<ResourceLocation> {
        lock.readLock().lock()
        try {
            return dynamicEntries.keys.toSet()
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * 获取所有动态条目
     */
    fun getDynamicEntries(): Map<ResourceLocation, T> {
        lock.readLock().lock()
        try {
            return dynamicEntries.toMap()
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * 检查是否为动态条目
     */
    fun isDynamic(key: ResourceLocation): Boolean {
        lock.readLock().lock()
        try {
            return dynamicEntries.containsKey(key)
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * 检查是否为动态条目（使用 ResourceKey）
     */
    fun isDynamic(key: ResourceKey<T>): Boolean {
        return isDynamic(key.location())
    }

    // 以下是 Registry<T> 接口的实现

    override fun key(): ResourceKey<out Registry<T>> {
        return staticRegistry.key()
    }

    override fun getKey(value: T): ResourceLocation? {
        // 检查是否已经在处理该对象，避免递归
        val identityHash = System.identityHashCode(value)
        val isProcessing = processingIds.get()
        if (isProcessing.contains(identityHash)) {
            // 已经在处理这个对象，返回默认值避免递归
            return null
        }

        isProcessing.add(identityHash)
        try {
            // 先查找动态条目，使用循环而不是 Map.get()
            lock.readLock().lock()
            try {
                // 使用循环遍历查找，避免调用 value.hashCode()
                for ((v, k) in dynamicValueToKey.entries) {
                    if (v === value) { // 使用引用相等而不是 equals()
                        return k
                    }
                }
            } finally {
                lock.readLock().unlock()
            }

            // 再查找静态条目
            return staticRegistry.getKey(value)
        } finally {
            isProcessing.remove(identityHash)
        }
    }

    override fun getResourceKey(value: T): Optional<ResourceKey<T>> {
        val key = getKey(value) ?: return Optional.empty()
        return Optional.of(ResourceKey.create(key(), key))
    }

    // 用于避免递归调用的标记
    private val processingIds = ThreadLocal.withInitial { HashSet<Int>() }

    override fun getId(value: T?): Int {
        if (value == null) return -1

        // 检查是否已经在处理该对象，避免递归
        val identityHash = System.identityHashCode(value)
        val isProcessing = processingIds.get()
        if (isProcessing.contains(identityHash)) {
            // 已经在处理这个对象，返回默认值避免递归
            return -1
        }

        isProcessing.add(identityHash)
        try {
            // 先查找动态条目，使用循环而不是 Map.get()
            lock.readLock().lock()
            try {
                // 使用循环遍历查找，避免调用 value.hashCode()
                for ((v, k) in dynamicValueToKey.entries) {
                    if (v === value) { // 使用引用相等而不是 equals()
                        return dynamicKeyToId[k] ?: -1
                    }
                }
            } finally {
                lock.readLock().unlock()
            }

            // 再查找静态条目
            return staticRegistry.getId(value)
        } finally {
            isProcessing.remove(identityHash)
        }
    }

    override fun get(key: ResourceKey<T>?): T? {
        if (key == null) return null

        // 先查找动态条目
        lock.readLock().lock()
        try {
            val dynamicValue = dynamicEntries[key.location()]
            if (dynamicValue != null) {
                return dynamicValue
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.get(key)
    }

    override fun get(name: ResourceLocation?): T? {
        if (name == null) return null

        // 先查找动态条目
        lock.readLock().lock()
        try {
            val dynamicValue = dynamicEntries[name]
            if (dynamicValue != null) {
                return dynamicValue
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.get(name)
    }

    override fun registrationInfo(key: ResourceKey<T>): Optional<RegistrationInfo> {
        // 动态条目使用 BUILT_IN 注册信息（因为没有 RUNTIME 常量）
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(key.location())) {
                return Optional.of(RegistrationInfo.BUILT_IN)
            }
        } finally {
            lock.readLock().unlock()
        }

        // 静态条目使用原始注册信息
        return staticRegistry.registrationInfo(key)
    }

    override fun registryLifecycle() = staticRegistry.registryLifecycle()

    override fun byNameCodec() = staticRegistry.byNameCodec()

    override fun holderByNameCodec() = staticRegistry.holderByNameCodec()

    override fun containsKey(name: ResourceLocation): Boolean {
        // 先查找动态条目
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(name)) {
                return true
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.containsKey(name)
    }

    override fun containsKey(key: ResourceKey<T>): Boolean {
        return containsKey(key.location())
    }

    override fun size(): Int {
        lock.readLock().lock()
        try {
            return staticRegistry.size() + dynamicEntries.size
        } finally {
            lock.readLock().unlock()
        }
    }

    override fun keySet(): Set<ResourceLocation> {
        val result = HashSet<ResourceLocation>()

        // 添加静态条目的键
        result.addAll(staticRegistry.keySet())

        // 添加动态条目的键
        lock.readLock().lock()
        try {
            result.addAll(dynamicEntries.keys)
        } finally {
            lock.readLock().unlock()
        }

        return result
    }

    override fun entrySet(): Set<Map.Entry<ResourceKey<T>, T>> {
        val result = HashSet<Map.Entry<ResourceKey<T>, T>>()

        // 添加静态条目
        result.addAll(staticRegistry.entrySet())

        // 添加动态条目
        lock.readLock().lock()
        try {
            dynamicEntries.forEach { (key, value) ->
                val resourceKey = ResourceKey.create(key(), key)
                result.add(object : Map.Entry<ResourceKey<T>, T> {
                    override val key: ResourceKey<T> = resourceKey
                    override val value: T = value
                })
            }
        } finally {
            lock.readLock().unlock()
        }

        return result
    }

    override fun registryKeySet(): Set<ResourceKey<T>> {
        val result = HashSet<ResourceKey<T>>()

        // 添加静态条目的键
        result.addAll(staticRegistry.registryKeySet())

        // 添加动态条目的键
        lock.readLock().lock()
        try {
            dynamicEntries.keys.forEach { key ->
                result.add(ResourceKey.create(key(), key))
            }
        } finally {
            lock.readLock().unlock()
        }

        return Collections.unmodifiableSet(result)
    }

    override fun getRandom(random: RandomSource): Optional<Holder.Reference<T>> {
        // 合并静态和动态条目的 holders
        val allHolders = ArrayList<Holder.Reference<T>>()

        // 添加静态条目
        staticRegistry.holders().forEach { allHolders.add(it) }

        // 添加动态条目（这里简化处理，实际上需要创建 Holder.Reference）
        // 由于复杂性，暂时只返回静态条目中的随机元素

        return if (allHolders.isEmpty()) {
            Optional.empty()
        } else {
            val randomIndex = random.nextInt(allHolders.size)
            Optional.of(allHolders[randomIndex])
        }
    }

    override fun freeze(): Registry<T> {
        // 只冻结静态部分，动态部分保持可变
        if (staticRegistry is WritableRegistry<*>) {
            (staticRegistry as WritableRegistry<T>).freeze()
        }
        return this
    }

    override fun createIntrusiveHolder(value: T): Holder.Reference<T> {
        // 对于动态条目，我们需要特殊处理
        lock.readLock().lock()
        try {
            val key = dynamicValueToKey[value]
            if (key != null) {
                // 这里可能需要创建一个特殊的 Holder.Reference 实现
                // 暂时使用静态注册表的方法
                return staticRegistry.createIntrusiveHolder(value)
            }
        } finally {
            lock.readLock().unlock()
        }

        // 对于静态条目，委托给静态注册表
        return staticRegistry.createIntrusiveHolder(value)
    }

    override fun getHolder(id: Int): Optional<Holder.Reference<T>> {
        // 先查找动态条目
        lock.readLock().lock()
        try {
            val key = dynamicIdToKey[id]
            if (key != null) {
                val value = dynamicEntries[key]
                if (value != null) {
                    val resourceKey = ResourceKey.create(key(), key)
                    // 这里需要创建一个 Holder.Reference
                    // 暂时返回静态注册表的 Holder
                    return staticRegistry.getHolder(resourceKey)
                }
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.getHolder(id)
    }

    override fun getHolder(location: ResourceLocation): Optional<Holder.Reference<T>> {
        // 先查找动态条目
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(location)) {
                val resourceKey = ResourceKey.create(key(), location)
                // 这里需要创建一个 Holder.Reference
                // 暂时返回静态注册表的 Holder
                return staticRegistry.getHolder(resourceKey)
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.getHolder(location)
    }

    override fun getHolder(key: ResourceKey<T>): Optional<Holder.Reference<T>> {
        return getHolder(key.location())
    }

    override fun wrapAsHolder(value: T): Holder<T> {
        // 对于动态条目，我们需要特殊处理
        lock.readLock().lock()
        try {
            val key = dynamicValueToKey[value]
            if (key != null) {
                // 这里可能需要创建一个特殊的 Holder 实现
                // 暂时使用静态注册表的方法
                return staticRegistry.wrapAsHolder(value)
            }
        } finally {
            lock.readLock().unlock()
        }

        // 对于静态条目，委托给静态注册表
        return staticRegistry.wrapAsHolder(value)
    }

    override fun holders(): Stream<Holder.Reference<T>> {
        // 合并静态和动态条目的 holders
        val staticHolders = staticRegistry.holders()

        // 动态条目的 holders 需要特殊处理
        // 暂时返回静态 holders
        return staticHolders
    }

    override fun getTag(key: TagKey<T>): Optional<HolderSet.Named<T>> {
        // 标签系统暂时委托给静态注册表
        return staticRegistry.getTag(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getTags(): Stream<Pair<TagKey<T>, HolderSet.Named<T>>> {
        // 标签系统暂时委托给静态注册表
        // 直接返回静态注册表的标签
        // 由于类型兼容性问题，这里使用类型转换
        return staticRegistry.getTags() as Stream<Pair<TagKey<T>, HolderSet.Named<T>>>
    }

    override fun getOrCreateTag(key: TagKey<T>): HolderSet.Named<T> {
        // 标签系统暂时委托给静态注册表
        return staticRegistry.getOrCreateTag(key)
    }

    override fun bindTags(tags: Map<TagKey<T>, MutableList<Holder<T>>>) {
        // 标签系统暂时委托给静态注册表
        staticRegistry.bindTags(tags)
    }

    override fun resetTags() {
        // 标签系统暂时委托给静态注册表
        staticRegistry.resetTags()
    }

    override fun getTagNames(): Stream<TagKey<T>> {
        // 标签系统暂时委托给静态注册表
        return staticRegistry.getTagNames()
    }

    override fun holderOwner(): HolderOwner<T> {
        // 委托给静态注册表
        return staticRegistry.holderOwner()
    }

    override fun asLookup(): HolderLookup.RegistryLookup<T> {
        // 委托给静态注册表
        return staticRegistry.asLookup()
    }

    // IRegistryExtension 接口实现

    override fun doesSync(): Boolean {
        return staticRegistry.doesSync()
    }

    override fun getMaxId(): Int {
        // 返回动态ID的最大值
        return nextDynamicId.get() - 1
    }

    override fun getId(key: ResourceKey<T>): Int {
        // 先查找动态条目
        lock.readLock().lock()
        try {
            val location = key.location()
            if (dynamicEntries.containsKey(location)) {
                return dynamicKeyToId[location] ?: -1
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.getId(key)
    }

    override fun getId(name: ResourceLocation): Int {
        // 先查找动态条目
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(name)) {
                return dynamicKeyToId[name] ?: -1
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.getId(name)
    }

    override fun containsValue(value: T): Boolean {
        // 先查找动态条目
        lock.readLock().lock()
        try {
            if (dynamicValueToKey.containsKey(value)) {
                return true
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.containsValue(value)
    }

    override fun <A> getData(type: DataMapType<T, A>, key: ResourceKey<T>): A? {
        // 数据映射暂时委托给静态注册表
        return staticRegistry.getData(type, key)
    }

    override fun <A> getDataMap(type: DataMapType<T, A>): Map<ResourceKey<T>, A> {
        // 数据映射暂时委托给静态注册表
        return staticRegistry.getDataMap(type)
    }

    // 别名解析支持

    override fun resolve(name: ResourceLocation): ResourceLocation {
        // 先检查动态条目
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(name)) {
                return name
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再委托给静态注册表
        return staticRegistry.resolve(name)
    }

    override fun resolve(key: ResourceKey<T>): ResourceKey<T> {
        val resolvedName = resolve(key.location())
        // 如果可能，尝试重用原始的 key
        return if (resolvedName == key.location()) key else ResourceKey.create(key(), resolvedName)
    }

    override fun getAny(): Optional<Holder.Reference<T>> {
        // 先检查动态条目
        lock.readLock().lock()
        try {
            if (dynamicEntries.isNotEmpty()) {
                // 返回第一个动态条目
                // 注意：这里简化处理，实际上需要创建 Holder.Reference
                // 暂时委托给静态注册表
                return staticRegistry.getAny()
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.getAny()
    }

    override fun byId(id: Int): T? {
        // 先检查动态条目
        lock.readLock().lock()
        try {
            val key = dynamicIdToKey[id]
            if (key != null) {
                return dynamicEntries[key]
            }
        } finally {
            lock.readLock().unlock()
        }

        // 再查找静态条目
        return staticRegistry.byId(id)
    }

    override fun iterator(): MutableIterator<T> {
        // 创建一个合并静态和动态条目的迭代器
        val staticIterator = staticRegistry.iterator()

        return object : MutableIterator<T> {
            private val dynamicValues = dynamicEntries.values.iterator()
            private var currentIterator = staticIterator

            override fun hasNext(): Boolean {
                if (currentIterator.hasNext()) return true
                if (currentIterator === staticIterator) {
                    currentIterator = dynamicValues
                    return currentIterator.hasNext()
                }
                return false
            }

            override fun next(): T {
                if (!hasNext()) throw NoSuchElementException()
                return currentIterator.next()
            }

            override fun remove() {
                throw UnsupportedOperationException("Cannot remove elements from registry iterator")
            }
        }
    }

    override fun addCallback(callback: RegistryCallback<T>) {
        // 委托给静态注册表
        staticRegistry.addCallback(callback)
    }

    override fun addAlias(from: ResourceLocation, to: ResourceLocation) {
        // 别名系统暂时委托给静态注册表
        staticRegistry.addAlias(from, to)
    }
}
