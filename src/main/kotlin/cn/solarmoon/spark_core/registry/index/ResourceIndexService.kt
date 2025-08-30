package cn.solarmoon.spark_core.registry.index

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 资源索引服务
 *
 * 用于热重载资源域的稳定、线程安全的 RL↔ID 索引。
 * 服务器权威分配由调用方 （DynamicIdManager） 强制执行。
 *
 *设计：
 * - 每个 registryName 映射到每个注册表的索引，其中包含 RL→ID 和 nextId 生成器
 * - 分配返回稳定的 ID;nextId 由 baseId 播种并单调增长
 * - applySyncedId 更新映射并根据需要增加 nextId
 * - 暂时在内存中;持久性可以稍后在世界保存路径中添加
 */
object ResourceIndexService {

    private data class Index(
        val rlToId: ConcurrentHashMap<ResourceLocation, Int> = ConcurrentHashMap(),
        val nextId: AtomicInteger = AtomicInteger(0)
    )

    private val indices = ConcurrentHashMap<String, Index>()

     /**
     * 确保注册表存在索引，如果较大，则从 baseId 植入 nextId。
     */
    private fun getIndex(registryName: String, baseId: Int): Index {
        val idx = indices.computeIfAbsent(registryName) { Index() }
        // Seed nextId to be at least baseId (max(staticMax+1, currentNext))
        while (true) {
            val cur = idx.nextId.get()
            if (cur >= baseId) break
            if (idx.nextId.compareAndSet(cur, baseId)) break
        }
        return idx
    }

    fun allocateId(registryName: String, key: ResourceLocation, baseId: Int): Int {
        val idx = getIndex(registryName, baseId)
        idx.rlToId[key]?.let { return it }

        val newId = idx.nextId.getAndIncrement()
        idx.rlToId.putIfAbsent(key, newId)?.let { return it }

        SparkCore.LOGGER.debug("ResourceIndexService.allocateId [{}:{}] -> {} (next={})", registryName, key, newId, idx.nextId.get())
        return newId
    }

    fun applySyncedId(registryName: String, key: ResourceLocation, id: Int) {
        val idx = getIndex(registryName, id + 1)
        idx.rlToId[key] = id
        // Ensure generator is ahead of assigned id
        while (true) {
            val cur = idx.nextId.get()
            if (cur > id) break
            if (idx.nextId.compareAndSet(cur, id + 1)) break
        }
        SparkCore.LOGGER.debug("ResourceIndexService.applySyncedId [{}:{}] -> {} (next={})", registryName, key, id, idx.nextId.get())
    }

    fun getId(registryName: String, key: ResourceLocation): Int? =
        indices[registryName]?.rlToId?.get(key)

    fun hasMapping(registryName: String, key: ResourceLocation): Boolean =
        indices[registryName]?.rlToId?.containsKey(key) == true

    fun getMappingsFor(registryName: String): Map<ResourceLocation, Int> =
        indices[registryName]?.rlToId?.toMap() ?: emptyMap()

    fun clearRegistry(registryName: String) {
        val removed = indices.remove(registryName)
        SparkCore.LOGGER.info("ResourceIndexService.clearRegistry {} removed={} entries", registryName, removed?.rlToId?.size ?: 0)
    }

    fun clearAll() {
        val total = indices.values.sumOf { it.rlToId.size }
        indices.clear()
        SparkCore.LOGGER.info("ResourceIndexService.clearAll removed {} entries across registries", total)
    }

    fun getRegistryStats(): Map<String, Int> =
        indices.mapValues { it.value.rlToId.size }

    fun getAllMappings(): Map<String, Map<String, Int>> =
        indices.mapValues { (_, idx) -> idx.rlToId.entries.associate { it.key.toString() to it.value } }
}
