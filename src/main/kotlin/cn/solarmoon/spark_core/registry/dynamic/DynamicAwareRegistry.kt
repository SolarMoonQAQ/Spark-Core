package cn.solarmoon.spark_core.registry.dynamic

import cn.solarmoon.spark_core.SparkCore
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Lifecycle
import net.minecraft.core.*
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.neoforged.neoforge.registries.callback.RegistryCallback
import net.neoforged.neoforge.registries.datamaps.DataMapType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.reflect.KClass

/**
 * 动态感知注册表
 *
 * 包装静态 Registry<T>，同时维护一个 ConcurrentHashMap 用于动态条目
 * 实现 Registry<T> 接口的关键查询方法，并提供 registerDynamic/unregisterDynamic 方法
 *
 * @param staticRegistry 被包装的静态注册表
 * @param valueType 注册表存储的元素的 KClass
 */
class DynamicAwareRegistry<T: Any>(
    private val staticRegistry: Registry<T>,
    val valueType: KClass<T>
) : Registry<T>, WritableRegistry<T> {

    private var isStaticPhaseOver: Boolean = false

    fun markStaticRegistrationComplete() {
        this.isStaticPhaseOver = true
        SparkCore.LOGGER.info("DynamicAwareRegistry for ${this.key().location()} has been marked as static phase complete.")
    }

    var onDynamicRegister: ((key: ResourceKey<T>, value: T) -> Unit)? = null
    var onDynamicUnregister: ((key: ResourceKey<T>, value: T) -> Unit)? = null

    // 动态条目存储
    private val dynamicEntries = ConcurrentHashMap<ResourceLocation, T>()
    private val dynamicKeyToId = ConcurrentHashMap<ResourceLocation, Int>()
    private val dynamicIdToKey = ConcurrentHashMap<Int, ResourceLocation>()
    private val dynamicValueToKey = ConcurrentHashMap<T, ResourceLocation>()
    // staticLookup 应该总是可以从 staticRegistry 获取，因为 Registry 接口定义了 createRegistrationLookup
    val staticLookup: HolderGetter<T>?

    // 线程安全锁
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    // 动态ID生成器，从最大静态ID开始递增
    private val nextDynamicId = AtomicInteger(findMaxStaticId() + 1)

    // 新增：用于存储动态条目的DataMapType数据
    private val dynamicDataMapValues = ConcurrentHashMap<ResourceLocation, MutableMap<DataMapType<T, *>, Any>>()

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

    fun registerDynamic(key: ResourceLocation, value: T, replace: Boolean = true): T {
        lock.writeLock().lock()
        try {
            if (!replace){
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
     * 注册一个条目
     *
     * @param key 资源位置
     * @param value 要注册的值
     * @return 注册的值
     */
    override fun register(key: ResourceKey<T>, value: T, info: RegistrationInfo): Holder.Reference<T> {
        lock.writeLock().lock()
        try {
            val location = key.location()
            if (this.isStaticPhaseOver) {
//                if (staticRegistry.containsKey(location)) {
//                    SparkCore.LOGGER.warn("Attempted to dynamically register key $location which already exists in the static registry. Returning existing static holder.")
//                    return staticRegistry.getHolderOrThrow(key)
//                }
//                if (dynamicEntries.containsKey(location)) {
//                    SparkCore.LOGGER.warn("Attempted to dynamically register key $location which already exists in dynamic entries. Returning existing dynamic holder.")
//                    return this.getHolderOrThrow(key) // Already a dynamic holder
//                }

                val id = nextDynamicId.getAndIncrement()
                dynamicEntries[location] = value
                dynamicKeyToId[location] = id
                dynamicIdToKey[id] = location
                dynamicValueToKey[value] = location

                val holder = Holder.Reference.createStandAlone(this.dynamicOwner, key)
                holder.value = value
                SparkCore.LOGGER.debug("Dynamically registered entry: {} with id {}", location, id)
                onDynamicRegister?.invoke(key, value) // Invoke callback
                return holder
            } else {
                // Static registration path
                if (staticRegistry is WritableRegistry<T>) {
                    return staticRegistry.register(key, value, info)
                } else {
                    SparkCore.LOGGER.error("Static registry is not writable, but registration for $location was attempted during static phase.")
                    throw IllegalStateException("Static registry is not an instance of WritableRegistry, cannot register statically.")
                }
            }
        } finally {
            lock.writeLock().unlock()
        }
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

            val existingValue = dynamicEntries[key] // Retrieve before removing
            val id = dynamicKeyToId[key]

            if (existingValue == null) { // Should not happen if containsKey passed, but good for safety
                return false
            }

            dynamicEntries.remove(key)
            dynamicKeyToId.remove(key)
            if (id != null) {
                dynamicIdToKey.remove(id)
            }
            dynamicValueToKey.remove(existingValue) // Remove by value

            // Construct ResourceKey for the callback
            val resourceKey = ResourceKey.create(this.key(), key)
            onDynamicUnregister?.invoke(resourceKey, existingValue) // Invoke callback
            SparkCore.LOGGER.debug("Dynamically unregistered entry: $key")
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
        return Optional.of(ResourceKey.create(this.key(), key))
    }

    // 用于避免递归调用的标记, 用于处理之前无限递归的情况
    private val processingIds = ThreadLocal.withInitial { HashSet<Int>() }

    /**
     * 当 DynamicAwareRegistry.getId(value) 被调用时，它尝试在 dynamicValueToKey 中查找 value
     * 这个查找操作会调用 value.hashCode() 方法（ConcurrentHashMap 需要计算哈希值）
     * 在 TypedAnimation 类中，hashCode() 方法之前会调用 getId() 方法
     * 而 TypedAnimation.getId() 又会调用 DynamicAwareRegistry.getId(this)
     * 这样就形成了无限递归
     */
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

    override fun byNameCodec(): Codec<T> { 
        return ResourceLocation.CODEC.flatXmap(
            { location: ResourceLocation -> 
                val value_ = this.get(location) 
                if (value_ != null) {
                    DataResult.success(value_)
                } else {
                    DataResult.error { "Unknown registry key in ${this.key().location()}: $location" }
                }
            },
            { value: T -> 
                val loc_ = this.getKey(value) 
                if (loc_ != null) {
                    DataResult.success(loc_)
                } else {
                    DataResult.error { "Value $value not found in registry ${this.key().location()} for byNameCodec" }
                }
            }
        )
    }

    override fun holderByNameCodec(): Codec<Holder<T>> {
        return ResourceKey.codec(this.key()).flatXmap(
            { key: ResourceKey<T> -> 
                this.getHolder(key) 
                    .map { holderRef: Holder.Reference<T> -> DataResult.success<Holder<T>>(holderRef) } 
                    .orElseGet { DataResult.error { "Unknown registry key in ${this.key().location()}: $key" } }
            },
            { holder: Holder<T> -> 
                holder.unwrapKey() 
                    .map { rKey: ResourceKey<T> -> DataResult.success(rKey) }
                    .orElseGet { DataResult.error { "Unregistered holder in ${this.key().location()} for holderByNameCodec: $holder" } }
            }
        )
    }

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
        lock.readLock().withLock {
            dynamicEntries.keys.forEach { key -> // key here is ResourceLocation
                result.add(ResourceKey.create(this.key(), key))
            }
        }

        return Collections.unmodifiableSet(result)
    }

    override fun getRandom(random: RandomSource): Optional<Holder.Reference<T>> {
        lock.readLock().withLock {
            val allHolders = this.holders().toList() // Leverages the already updated holders() method
            return if (allHolders.isEmpty()) {
                Optional.empty()
            } else {
                // Ensure distinct holders if there's a chance of overlap
                val distinctHolders = allHolders.distinctBy { it.key() }
                if (distinctHolders.isEmpty()) { // Check again after distinct
                    Optional.empty()
                } else {
                    val randomIndex = random.nextInt(distinctHolders.size)
                    Optional.of(distinctHolders[randomIndex])
                }
            }
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
            for ((entryValue, dynamicLocation) in dynamicValueToKey.entries) {
                if (entryValue === value) {
                    val resourceKey = ResourceKey.create(this.key(), dynamicLocation)
                    val holder = Holder.Reference.createStandAlone(this.dynamicOwner, resourceKey)
                    holder.value = value
                    return holder
                }
            }
        } finally {
            lock.readLock().unlock()
        }

        // 对于静态条目，委托给静态注册表
        return staticRegistry.createIntrusiveHolder(value)
    }

    private inner class DynamicHolderOwner : HolderOwner<T> {
        override fun canSerializeIn(owner: HolderOwner<T>): Boolean {
            return owner == this
        }
    }

    private val dynamicOwner: HolderOwner<T> = DynamicHolderOwner()

    override fun getHolder(id: Int): Optional<Holder.Reference<T>> {
        lock.readLock().lock()
        try {
            val location = dynamicIdToKey[id]
            if (location != null && dynamicEntries.containsKey(location)) {
                val dynamicKey = ResourceKey.create(this.key(), location)
                val holderReference = Holder.Reference.createStandAlone(dynamicOwner, dynamicKey)
                return Optional.of(holderReference)
            }
        } finally {
            lock.readLock().unlock()
        }
        return staticRegistry.getHolder(id)
    }

    override fun getHolder(location: ResourceLocation): Optional<Holder.Reference<T>> {
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(location)) {
                val dynamicKey = ResourceKey.create(this.key(), location)
                val holderReference = Holder.Reference.createStandAlone(dynamicOwner, dynamicKey)
                return Optional.of(holderReference)
            }
        } finally {
            lock.readLock().unlock()
        }
        return staticRegistry.getHolder(location)
    }

    override fun getHolder(key: ResourceKey<T>): Optional<Holder.Reference<T>> {
        if (key.registry() != this.key().location()) {
            // Comments from before
        }

        lock.readLock().lock()
        try {
            // Check if the key (which is ResourceKey<T>) directly corresponds to a dynamic entry.
            // Ensure the key's registry matches this registry's main key.
            if (key.isFor(this.key()) && dynamicEntries.containsKey(key.location())) {
                // 'key' is already the correct ResourceKey<T> for the dynamic entry.
                val holderReference = Holder.Reference.createStandAlone(dynamicOwner, key)
                return Optional.of(holderReference)
            }
        } finally {
            lock.readLock().unlock()
        }
        return staticRegistry.getHolder(key)
    }

    override fun getHolderOrThrow(key: ResourceKey<T>): Holder.Reference<T> {
        lock.readLock().lock()
        try {
            if (dynamicEntries.containsKey(key.location())) {
                return Holder.Reference.createStandAlone(this.dynamicOwner, key)
            }
        } finally {
            lock.readLock().unlock()
        }
        return staticRegistry.getHolderOrThrow(key)
    }

    override fun getAny(): Optional<Holder.Reference<T>> {
        lock.readLock().lock()
        try {
            if (dynamicEntries.isNotEmpty()) {
                val firstLocation = dynamicEntries.keys.firstOrNull()
                if (firstLocation != null) {
                    val dynamicKey = ResourceKey.create(this.key(), firstLocation)
                    val holderReference = Holder.Reference.createStandAlone(dynamicOwner, dynamicKey)
                    return Optional.of(holderReference)
                }
            }
        } finally {
            lock.readLock().unlock()
        }
        return staticRegistry.getAny()
    }

    override fun wrapAsHolder(value: T): Holder<T> {
        // 对于动态条目，我们需要特殊处理
        lock.readLock().withLock {
            dynamicValueToKey[value]?.let { location ->
                val key = ResourceKey.create(this.key(), location)
                // For dynamic entries, create a stand-alone holder if it's registered dynamically
                if (dynamicEntries.containsKey(location)) {
                    return Holder.Reference.createStandAlone(this.dynamicOwner, key)
                }
            }
        }
        // 对于静态条目或未动态注册的，委托给静态注册表
        return staticRegistry.wrapAsHolder(value)
    }

    override fun holders(): Stream<Holder.Reference<T>> {
        // 合并静态和动态条目的 holders
        val staticHoldersStream = staticRegistry.holders()

        val dynamicHoldersStream = lock.readLock().withLock {
            dynamicEntries.keys.stream().map { location ->
                val dynamicKey = ResourceKey.create(this.key(), location)
                Holder.Reference.createStandAlone(this.dynamicOwner, dynamicKey)
            }
        }

        return Stream.concat(staticHoldersStream, dynamicHoldersStream)
            .collect(Collectors.toMap(
                { holder -> holder.key() },
                { holder -> holder },
                { existing, _ -> existing },
                Supplier { LinkedHashMap<ResourceKey<T>, Holder.Reference<T>>() }
            ))
            .values
            .stream()
    }

    // --- Tag Management --- //

    private val dynamicTagToHolders: MutableMap<TagKey<T>, List<Holder<T>>> = ConcurrentHashMap()

    override fun getOrCreateTag(tagKey: TagKey<T>): HolderSet.Named<T> {
        lock.readLock().withLock {
            val dynamicHoldersList = dynamicTagToHolders[tagKey]
            val staticHolderSetOpt = staticRegistry.getTag(tagKey)

            return when {
                dynamicHoldersList != null && staticHolderSetOpt.isPresent -> MergedHolderSet(this.dynamicOwner, tagKey, staticHolderSetOpt.get(), dynamicHoldersList)
                dynamicHoldersList != null -> DynamicOnlyHolderSet(this.dynamicOwner, tagKey, dynamicHoldersList)
                else -> staticRegistry.getOrCreateTag(tagKey)
            }
        }
    }

    override fun getTag(tagKey: TagKey<T>): Optional<HolderSet.Named<T>> {
        lock.readLock().withLock {
            val dynamicHoldersList = dynamicTagToHolders[tagKey]
            val staticHolderSetOpt = staticRegistry.getTag(tagKey)

            return when {
                dynamicHoldersList != null && staticHolderSetOpt.isPresent -> Optional.of(MergedHolderSet(this.dynamicOwner, tagKey, staticHolderSetOpt.get(), dynamicHoldersList))
                dynamicHoldersList != null -> Optional.of(DynamicOnlyHolderSet(this.dynamicOwner, tagKey, dynamicHoldersList))
                staticHolderSetOpt.isPresent -> staticHolderSetOpt
                else -> Optional.empty()
            }
        }
    }

    override fun getTags(): Stream<Pair<TagKey<T>, HolderSet.Named<T>>> {
        lock.readLock().withLock {
            val staticTagsStream = staticRegistry.tags

            val staticTagKeys = staticRegistry.tags.map { it.first }.collect(Collectors.toSet())

            val dynamicOnlyTagsStream = dynamicTagToHolders.keys
                .filterNot { it in staticTagKeys }
                .stream()
                .map { tagKey -> Pair(tagKey, getOrCreateTag(tagKey)) }

            return Stream.concat(staticTagsStream, dynamicOnlyTagsStream).distinct()
        }
    }

    override fun getTagNames(): Stream<TagKey<T>> {
        lock.readLock().withLock {
            val staticTagNames = staticRegistry.tagNames // Stream<TagKey<T>> from underlying registry
            val dynamicTagNames = dynamicTagToHolders.keys.stream() // Stream<TagKey<T>> from our dynamic map
            return Stream.concat(staticTagNames, dynamicTagNames).distinct()
        }
    }

    override fun getTagOrEmpty(tagKey: TagKey<T>): Iterable<Holder<T>> {
        return this.getTag(tagKey).orElse(null) ?: emptyList()
    }

    override fun bindTags(tags: Map<TagKey<T>, List<Holder<T>>>) {
        lock.writeLock().withLock {
            dynamicTagToHolders.clear()
            tags.forEach { (tagKey, holders) ->
                dynamicTagToHolders[tagKey] = ArrayList(holders)
            }
            (staticRegistry as WritableRegistry<T>).bindTags(tags)
        }
    }

    override fun resetTags() {
        lock.writeLock().withLock {
            dynamicTagToHolders.clear()
            (staticRegistry as WritableRegistry<T>).resetTags()
        }
    }

    // --- Builtin Registries --- //

    override fun holderOwner(): HolderOwner<T> {
        return this.dynamicOwner
    }

    // Inner class for HolderLookup.RegistryLookup implementation
    private inner class DynamicRegistryLookup : HolderLookup.RegistryLookup<T> {
        override fun key(): ResourceKey<out Registry<T>> {
            return this@DynamicAwareRegistry.key()
        }

        override fun registryLifecycle(): Lifecycle { // Changed from lifecycle() by USER
            return this@DynamicAwareRegistry.registryLifecycle()
        }
        override fun get(key: ResourceKey<T>): Optional<Holder.Reference<T>> {
            return this@DynamicAwareRegistry.getHolder(key)
        }

        override fun listElements(): Stream<Holder.Reference<T>> {
            return this@DynamicAwareRegistry.holders()
        }

        override fun listTags(): Stream<HolderSet.Named<T>> {
            return this@DynamicAwareRegistry.getTags().map { it.second }
        }

        override fun get(tagKey: TagKey<T>): Optional<HolderSet.Named<T>> {
            return this@DynamicAwareRegistry.getTag(tagKey)
        }
    }

    override fun asLookup(): HolderLookup.RegistryLookup<T> {
        return DynamicRegistryLookup()
    }

    override fun doesSync(): Boolean {
        return staticRegistry.doesSync()
    }

    override fun getMaxId(): Int {
        lock.readLock().withLock {
            val maxDynamicIdFromMap = dynamicIdToKey.keys.maxOrNull() ?: -1
            return max(maxDynamicIdFromMap, staticRegistry.maxId)
        }
    }

    override fun addCallback(callback: RegistryCallback<T?>) {
        staticRegistry.addCallback(callback)
    }

    override fun getId(key: ResourceKey<T>): Int {
        lock.readLock().withLock {
            val location = key.location()
            if (dynamicEntries.containsKey(location)) {
                return dynamicKeyToId[location] ?: -1
            }
        }

        return staticRegistry.getId(key)
    }

    override fun getId(name: ResourceLocation): Int {
        lock.readLock().withLock {
            if (dynamicEntries.containsKey(name)) {
                return dynamicKeyToId[name] ?: -1
            }
        }

        return staticRegistry.getId(name)
    }

    override fun containsValue(value: T): Boolean {
        lock.readLock().withLock {
            if (dynamicValueToKey.containsKey(value)) {
                return true
            }
        }

        return staticRegistry.containsValue(value)
    }

    override fun <A> getData(type: DataMapType<T, A>, key: ResourceKey<T>): A? {
        return lock.readLock().withLock {
            dynamicDataMapValues[key.location()]?.get(type)?.let { value ->
                // 我们无法直接获取 type.valueClass()。可以使用 isInstance 或依赖类型转换。
                // 运行时检查可以这样做：if (type.codec().decode(JsonOps.INSTANCE, someJsonRepresentationOfValue).result().isPresentAnd(decoded -> decoded.javaClass.isInstance(value))) { ... }
                // 然而，'value' 已经是 Any 类型。在这里通常采用简单的类型转换。
                // 我们假设数据已经以正确的类型放入。
                try {
                    @Suppress("UNCHECKED_CAST")
                    return@withLock value as A
                } catch (e: ClassCastException) {
                    SparkCore.LOGGER.warn(
                        "Type mismatch for DataMapType ${type.id()} and key $key. Value $value could not be cast to the expected type.",
                        e
                    )
                    // 如果类型不匹配则继续执行下一步
                }
            }
            // 2. 如果在动态注册表中未找到或类型不匹配，并且静态注册表是 IRegistryExtension，则委托给静态注册表
            (staticRegistry as? net.neoforged.neoforge.registries.IRegistryExtension<T>)?.getData(type, key)?.let {
                return@withLock it
            }
            // 如果 staticRegistry 不是 IRegistryExtension 或没有数据，则返回 null
            return@withLock null
        }
    }

    override fun <A> getDataMap(type: DataMapType<T, A>): Map<ResourceKey<T>, A> {
        return lock.readLock().withLock {
            val staticMap = staticRegistry.getDataMap(type) ?: emptyMap()
            val resultMap = mutableMapOf<ResourceKey<T>, A>()
            resultMap.putAll(staticMap)

            dynamicDataMapValues.forEach { (location, typeMap) -> // location is ResourceLocation from dynamicDataMapValues
                typeMap[type]?.let { value ->
                    try {
                        resultMap[ResourceKey.create(this.key(), location)] =
                            value as A // Construct ResourceKey for the final map
                    } catch (e: ClassCastException) {
                        SparkCore.LOGGER.warn(
                            "Type mismatch in dynamic data for DataMapType ${type.id()} and key $location. Value $value could not be cast.",
                            e
                        )
                    }
                }
            }
            return@withLock resultMap.toMap()
        }
    }

    override fun resolve(name: ResourceLocation): ResourceLocation {
        lock.readLock().withLock {
            if (dynamicEntries.containsKey(name)) {
                return name
            }
        }

        return staticRegistry.resolve(name)
    }

    override fun resolve(key: ResourceKey<T>): ResourceKey<T> {
        val resolvedName = resolve(key.location())
        return if (resolvedName == key.location()) key else ResourceKey.create(key(), resolvedName)
    }

    override fun byId(id: Int): T? {
        lock.readLock().withLock {
            val key = dynamicIdToKey[id]
            if (key != null) {
                return dynamicEntries[key]
            }
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

    override fun addAlias(from: ResourceLocation, to: ResourceLocation) {
        // Delegate to staticRegistry if it's an IRegistryExtension
        (staticRegistry as? net.neoforged.neoforge.registries.IRegistryExtension<T>)?.addAlias(from, to)
            ?: SparkCore.LOGGER.warn("staticRegistry is not an IRegistryExtension or does not support addAlias, cannot add alias for $from -> $to via it.")
    }

    // Inner classes for custom HolderSet implementations
    private class DynamicOnlyHolderSet<T>(
        owner: HolderOwner<T>,
        tagKey: TagKey<T>,
        private val dynamicContentsList: List<Holder<T>>
    ) : HolderSet.Named<T>(owner, tagKey) {

        init {
            this.bind(dynamicContentsList)
        }

        override fun toString(): String = "DynamicOnlyHolderSet(${this.key.location()})[${this.contents()}]"
    }

    private class MergedHolderSet<T>(
        owner: HolderOwner<T>,
        tagKey: TagKey<T>,
        private val staticSourceSet: HolderSet.Named<T>?,
        private val additionalDynamicHolders: List<Holder<T>>
    ) : HolderSet.Named<T>(owner, tagKey) {

        private val combinedContents: List<Holder<T>> by lazy {
            val combined = mutableListOf<Holder<T>>()
            staticSourceSet?.stream()?.forEach(combined::add)
            combined.addAll(additionalDynamicHolders)
            combined.distinctBy { it }
        }

        init {
            this.bind(combinedContents)
        }

        override fun toString(): String = "MergedHolderSet(${this.key.location()})[${this.contents()}]"
    }

    override fun isEmpty(): Boolean {
        // 为了线程安全地访问 dynamicEntries，我们需要获取读锁。
        this.lock.readLock().withLock {
            return if (staticRegistry is WritableRegistry){
                staticRegistry.isEmpty && this.dynamicEntries.isEmpty()
            }else{
                this.dynamicEntries.isEmpty()
            }
        }
    }


//    override fun createRegistrationLookup(): HolderGetter<T?> {
//        return object : HolderGetter<T?> {
//            override fun get(p_259097_: ResourceKey<T?>): Optional<Holder.Reference<T?>?> {
//                return Optional.of<Holder.Reference<T?>?>(this.getOrThrow(p_259097_))
//            }
//
//            override fun getOrThrow(p_259750_: ResourceKey<T?>): Holder.Reference<T?> {
//                return this@DynamicAwareRegistry.getOrCreateHolderOrThrow(p_259750_)
//            }
//
//            override fun get(p_259486_: TagKey<T?>): Optional<HolderSet.Named<T?>?> {
//                return Optional.of<HolderSet.Named<T?>?>(this.getOrThrow(p_259486_))
//            }
//
//            override fun getOrThrow(p_260298_: TagKey<T?>): HolderSet.Named<T?> {
//                return this@DynamicAwareRegistry.getOrCreateTag(p_260298_)
//            }
//        }
//    }
    override fun createRegistrationLookup(): HolderGetter<T> {
        return object : HolderGetter<T> {
            override fun get(lookupKey: ResourceKey<T>): Optional<Holder.Reference<T>> {
                // 1. 检查提供的 ResourceKey 是否针对当前这个注册表实例。
                if (!lookupKey.isFor(this@DynamicAwareRegistry.key())) {
                    return Optional.empty()
                }

                // 2. 尝试从动态条目中查找。
                val dynamicValueFromMap: T? = this@DynamicAwareRegistry.lock.readLock().withLock {
                    this@DynamicAwareRegistry.dynamicEntries[lookupKey.location()]
                }

                if (dynamicValueFromMap != null) {
                    // 2a. 如果在动态条目中找到了值：
                    val holder = Holder.Reference.createStandAlone(this@DynamicAwareRegistry.dynamicOwner, lookupKey)
                    // 2b. 将检索到的动态值绑定到 Holder 上。 (依赖AT使 'value' 字段可公开写)
                    holder.value = dynamicValueFromMap
                    return Optional.of(holder)
                }

                // 3. 如果在动态条目中未找到，则委托给静态注册表的查找器。
                if (staticLookup != null) {
                    return staticLookup.get(lookupKey)
                } else {
                    return Optional.empty() // Can't lookup if staticLookup is null
                }
            }

            // 匹配 HolderGetter<T> 接口的 get(TagKey<T>) 方法
            override fun get(tagKey: TagKey<T>): Optional<HolderSet.Named<T>> {
                if (staticLookup != null) {
                    return staticLookup.get(tagKey)
                } else {
                    return Optional.empty() // Can't lookup tags if staticLookup is null
                }
            }
        }
    }

    init {
        if (staticRegistry is MappedRegistry<T>) {
            staticLookup = staticRegistry.createRegistrationLookup()
        } else {
            SparkCore.LOGGER.warn("Static registry for ${this.key().location()} is of type ${staticRegistry::class.java.name}, which may not directly provide a HolderGetter. Static lookups might be limited if not a MappedRegistry.")
            staticLookup = null
        }
    }
}
