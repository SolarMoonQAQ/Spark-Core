package cn.solarmoon.spark_core.resource.common

import cn.solarmoon.spark_core.SparkCore
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 多模组资源注册管理器
 * 
 * 负责管理所有注册到SparkCore资源系统的模组信息
 * 提供线程安全的注册、查询和管理功能
 */
object MultiModResourceRegistry {
    
    /**
     * 已注册的模组资源信息列表
     * 使用CopyOnWriteArrayList确保线程安全，支持并发读取和修改
     */
    private val registeredMods = CopyOnWriteArrayList<ModResourceInfo>()
    
    /**
     * 注册模组资源信息
     * 
     * 供下游mod调用，注册自己的资源信息到SparkCore资源系统
     * 
     * @param modId 模组ID，用作资源命名空间
     * @param modMainClass 模组主类，用于资源提取和类加载器访问
     */
    fun registerModResources(modId: String, modMainClass: Class<*>) {
        if (modId.isBlank()) {
            SparkCore.LOGGER.warn("尝试注册空的modId，忽略注册请求")
            return
        }
        
        // 检查是否已经注册过相同的mod
        if (isModRegistered(modId)) {
            SparkCore.LOGGER.warn("模组 {} 已经注册过，忽略重复注册请求", modId)
            return
        }
        
        val modInfo = ModResourceInfo(modId, modMainClass)
        registeredMods.add(modInfo)
        
        SparkCore.LOGGER.info("成功注册模组资源: {} (主类: {})", modId, modMainClass.simpleName)
    }
    
    /**
     * 获取所有已注册的模组资源信息
     * 
     * @return 已注册模组信息的不可变列表
     */
    fun getRegisteredMods(): List<ModResourceInfo> {
        return Collections.unmodifiableList(ArrayList(registeredMods))
    }
    
    /**
     * 获取已注册模组的数量
     * 
     * @return 已注册模组数量
     */
    fun getRegisteredModCount(): Int {
        return registeredMods.size
    }
    
    /**
     * 检查指定模组是否已注册
     * 
     * @param modId 模组ID
     * @return 如果已注册返回true，否则返回false
     */
    fun isModRegistered(modId: String): Boolean {
        return registeredMods.any { it.modId == modId }
    }
    
    /**
     * 根据modId获取模组信息
     * 
     * @param modId 模组ID
     * @return 模组信息，如果未找到返回null
     */
    fun getModInfo(modId: String): ModResourceInfo? {
        return registeredMods.find { it.modId == modId }
    }
    
    /**
     * 获取所有已注册的modId列表
     * 
     * @return modId列表
     */
    fun getRegisteredModIds(): List<String> {
        return registeredMods.map { it.modId }
    }
    
    /**
     * 清空所有已注册的模组（主要用于测试）
     * 
     * 注意：此方法应谨慎使用，通常只在测试环境中使用
     */
    fun clearAllRegistrations() {
        registeredMods.clear()
        SparkCore.LOGGER.warn("已清空所有模组注册信息")
    }
    
    /**
     * 获取注册统计信息
     * 
     * @return 包含统计信息的字符串
     */
    fun getRegistrationStats(): String {
        val modCount = registeredMods.size
        val modIds = registeredMods.joinToString(", ") { it.modId }
        return "已注册模组数量: $modCount, 模组列表: [$modIds]"
    }
}
