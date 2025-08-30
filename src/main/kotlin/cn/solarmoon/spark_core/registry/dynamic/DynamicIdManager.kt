package cn.solarmoon.spark_core.registry.dynamic

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.index.ResourceIndexService
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 统一的动态ID管理器
 * 负责为动态注册的资源分配全局唯一的ID
 * 
 * 核心原则：
 * - 服务端权威：所有ID由服务端/服务端线程分配
 * - 场景区分：正确处理集成服务器vs专用服务器
 * - 内存共享：集成服务器两端共享同一映射表，无需网络同步
 */
object DynamicIdManager {

    // 旧字段保留仅为向后兼容（避免运行期NPE）；逻辑由 ResourceIndexService 接管
    private val globalIdMapping = ConcurrentHashMap<Pair<String, ResourceLocation>, Int>()
    private val idGenerators = ConcurrentHashMap<String, AtomicInteger>()
    
    /**
     * 判断当前是否有权分配ID
     * 
     * 规则：
     * - 专用服务器：服务端有权
     * - 集成服务器：仅服务端线程有权
     * - 客户端：无权
     */
    fun isAuthoritative(): Boolean {
        val server = ServerLifecycleHooks.getCurrentServer()
        return when {
            server == null -> false
            server.isDedicatedServer() -> true // 专用服务器：服务端有权
            else -> server.isSameThread()      // 集成服务器：仅服务端线程有权
        }
    }
    
    /**
     * 判断是否需要网络同步
     * 
     * 规则：
     * - 专用服务器：需要网络同步
     * - 集成服务器：通过内存共享，无需网络同步
     */
    fun shouldSendSync(): Boolean {
        val server = ServerLifecycleHooks.getCurrentServer()
        // 只有专用服务器需要网络同步，集成服务器通过内存共享
        return server?.isDedicatedServer() ?: false
    }
    
    /**
     * 为资源分配ID
     * 
     * @param registryName 注册表名称（如"typed_animation"）
     * @param key 资源位置
     * @param baseId 该注册表的基础ID（通常是最大静态ID+1）
     * @return 分配的ID，如果无权分配则返回-1
     */
    fun allocateId(registryName: String, key: ResourceLocation, baseId: Int): Int {
        val mapKey = registryName to key

        // 已存在映射（兼容旧路径）；优先返回
        globalIdMapping[mapKey]?.let { return it }

        // 只有服务端（或集成服服务端线程）能分配
        if (!isAuthoritative()) {
            SparkCore.LOGGER.debug("客户端跳过ID分配: {} -> {} (等待同步)", registryName, key)
            return -1
        }

        // 经由稳定索引服务分配
        val newId = ResourceIndexService.allocateId(registryName, key, baseId)

        // 写入兼容映射（供旧调用路径读取）
        globalIdMapping[mapKey] = newId
        // 同步兼容生成器游标（可选，不再用于分配）
        idGenerators.computeIfAbsent(registryName) { AtomicInteger(newId + 1) }

        SparkCore.LOGGER.info(
            "✅ ID分配成功: [{}:{}] -> ID:{} (线程:{}, 基础ID:{})",
            registryName, key, newId, Thread.currentThread().name, baseId
        )

        return newId
    }
    
    /**
     * 客户端接收同步的ID（仅专用服务器模式）
     * 
     * @param registryName 注册表名称
     * @param key 资源位置
     * @param id 服务端分配的ID
     */
    fun applySyncedId(registryName: String, key: ResourceLocation, id: Int) {
        val mapKey = registryName to key
        globalIdMapping[mapKey] = id
        ResourceIndexService.applySyncedId(registryName, key, id)

        SparkCore.LOGGER.info(
            "📥 ID同步接收: [{}:{}] -> ID:{} (映射表大小:{})",
            registryName, key, id, globalIdMapping.size
        )
    }
    
    /**
     * 获取已分配的ID
     * 
     * @param registryName 注册表名称
     * @param key 资源位置
     * @return 已分配的ID，如果不存在则返回null
     */
    fun getId(registryName: String, key: ResourceLocation): Int? {
        // 优先从稳定索引读取
        return ResourceIndexService.getId(registryName, key)
            ?: globalIdMapping[registryName to key]
    }
    
    /**
     * 清理特定注册表的ID映射
     * 用于热重载或清理操作
     * 
     * @param registryName 注册表名称
     */
    fun clearRegistry(registryName: String) {
        val removedCount = globalIdMapping.keys.count { it.first == registryName }
        globalIdMapping.keys.removeIf { it.first == registryName }
        idGenerators.remove(registryName)
        ResourceIndexService.clearRegistry(registryName)
        SparkCore.LOGGER.info("清理注册表ID映射: {} (移除 {} 个条目)", registryName, removedCount)
    }
    
    /**
     * 调试方法：打印所有ID映射
     * 用于排查问题
     */
    fun debugPrintMappings() {
        SparkCore.LOGGER.info("=== DynamicIdManager ID映射表 ===")
        globalIdMapping.forEach { (key, id) ->
            SparkCore.LOGGER.info("  {}:{} -> {}", key.first, key.second, id)
        }
        SparkCore.LOGGER.info("=== 总计: {} 个映射 ===", globalIdMapping.size)
    }
    
    /**
     * 获取注册表的统计信息
     */
    fun getRegistryStats(): Map<String, Int> {
        return ResourceIndexService.getRegistryStats()
    }
    
    /**
     * 获取所有ID映射的详细信息
     */
    fun getAllMappings(): Map<String, Map<String, Int>> {
        return ResourceIndexService.getAllMappings()
    }

    /**
     * 获取指定注册表的所有映射（只读快照）
     */
    fun getMappingsForRegistry(registryName: String): Map<ResourceLocation, Int> {
        return ResourceIndexService.getMappingsFor(registryName)
    }
    
    /**
     * 检查特定资源是否已分配ID
     */
    fun hasMapping(registryName: String, key: ResourceLocation): Boolean {
        return globalIdMapping.containsKey(registryName to key)
    }
    
    /**
     * 客户端专用清理方法
     * 用于客户端世界切换时清理所有ID映射
     */
    fun clearAllForClient() {
        if (isAuthoritative()) {
            SparkCore.LOGGER.warn("服务端不应调用客户端清理方法")
            return
        }
        
        val totalCount = globalIdMapping.size
        globalIdMapping.clear()
        idGenerators.clear()
        ResourceIndexService.clearAll()
        SparkCore.LOGGER.info("客户端清理所有ID映射，共 {} 个条目", totalCount)
    }
    
    /**
     * 强制清除所有映射（仅用于调试）
     */
    fun clearAllMappings() {
        if (!isAuthoritative()) {
            SparkCore.LOGGER.warn("尝试从非权威端清除所有ID映射")
            return
        }
        
        val count = globalIdMapping.size
        globalIdMapping.clear()
        idGenerators.clear()
        ResourceIndexService.clearAll()
        SparkCore.LOGGER.warn("强制清除了所有ID映射，共 {} 个条目", count)
    }
}
