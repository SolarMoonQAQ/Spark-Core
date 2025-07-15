package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.ServerSparkJS
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.js.sync.JSIncrementalSyncS2CPacket
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.common.*
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.resource.payload.resource_sync.ChangeType
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil
import net.minecraft.core.RegistrationInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import kotlin.io.path.readText

/**
 * 重构后的JavaScript处理器
 * 使用统一的接口和服务，标准化路径解析和资源管理
 */
@AutoRegisterHandler
class JavaScriptHandler(
    private val jsRegistry: DynamicAwareRegistry<OJSScript>
) : ResourceHandlerBase() {

    private val resourceType = "scripts"
    private val supportedExtensions = setOf("js")
    private var processedCount = 0

    init {
        SparkCore.LOGGER.info("JavaScriptHandler 初始化完成")
        // 注意：移除对DependencyGraph的注册，使用MetadataManager进行依赖管理
    }

    // ===== 基础接口实现 =====

    override fun getResourceType(): String = resourceType

    override fun getRegistryIdentifier(): ResourceLocation? = jsRegistry.key().location()

    override fun getSupportedExtensions(): Set<String> = supportedExtensions

    override fun getPriority(): Int = 30 // 中等优先级

    // 提供对注册表的访问 (for DynamicResourceApplier)
    val jsRegistryAccess: DynamicAwareRegistry<OJSScript>
        get() = this.jsRegistry

    // ===== 资源处理核心逻辑 =====

    override fun processResourceAdded(node: ResourceNode) {
        try {
            val scriptContent = node.basePath.resolve(node.relativePath).readText()

            // 从路径中提取API ID
            val apiId = extractApiIdFromPath(node.id)

            // 创建JS脚本对象
            val jsScript = OJSScript(
                apiId = apiId,
                fileName = node.id.path.substringAfterLast('/'),
                content = scriptContent,
                location = node.id
            )

            // 存储到Origin映射
            OJSScript.ORIGINS[node.id] = jsScript

            // 增加处理计数
            processedCount++

            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 添加到模块资源映射
            addResourceToModule(moduleId, node.id)

            SparkCore.LOGGER.debug("处理JavaScript资源: ${node.id} (模块: $moduleId)")
            registerToRegistry(node.id, jsScript)
            // 如果初始扫描完成，注册到动态注册表
            if (isInitialScanComplete()) {
                // 同步到客户端
                syncToClients(node.id, jsScript, ChangeType.ADDED)

                // 注意：脚本执行现在由SparkJsApplier在LevelEvent.Load时处理
                // 这确保了JS组件在脚本执行前已经注册到JS引擎
            }

        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }

    override fun processResourceModified(node: ResourceNode) {
        try {
            // 先移除旧脚本
            val oldScript = OJSScript.ORIGINS[node.id]
            if (oldScript != null) {
                unloadScript(node.id, oldScript)
            }

            // 重新添加
            processResourceAdded(node)

        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }

    override fun processResourceRemoved(node: ResourceNode) {
        val resourceId = node.id
        val removed = OJSScript.ORIGINS.remove(resourceId)
        if (removed != null) {
            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 从模块资源映射中移除
            removeResourceFromModule(moduleId, resourceId)

            if (isInitialScanComplete()) {
                unregisterFromRegistry(resourceId)
                syncToClients(resourceId, removed, ChangeType.REMOVED)
                unloadScript(resourceId, removed)
            }
        } else {
            SparkCore.LOGGER.warn("尝试移除不存在的脚本: $resourceId")
        }
    }

    // ===== JS脚本管理 =====

    /**
     * 获取单例的ServerSparkJS实例，确保线程安全
     */
    private fun getServerJSInstance(): ServerSparkJS {
        return serverJSInstance ?: synchronized(this) {
            serverJSInstance ?: ServerSparkJS().also { serverJSInstance = it }
        }
    }

    /**
     * 单例的ServerSparkJS实例
     */
    @Volatile
    private var serverJSInstance: ServerSparkJS? = null

    private fun registerToRegistry(location: ResourceLocation, script: OJSScript) {
        try {
            val resourceKey = ResourceKey.create(jsRegistry.key(), location)
            jsRegistry.register(resourceKey, script, RegistrationInfo.BUILT_IN)

            SparkCore.LOGGER.debug("脚本已注册到注册表: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("REGISTER", location.toString(), e)
        }
    }

    private fun unregisterFromRegistry(location: ResourceLocation) {
        try {
            val resourceKey = ResourceKey.create(jsRegistry.key(), location)
            jsRegistry.unregisterDynamic(resourceKey)

            SparkCore.LOGGER.debug("脚本已从注册表注销: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("UNREGISTER", location.toString(), e)
        }
    }

    private fun executeScript(script: OJSScript) {
        try {
            val server = ServerLifecycleHooks.getCurrentServer()
            if (server != null) {
                // 使用单例的ServerSparkJS实现线程安全执行
                val serverJS = getServerJSInstance()
                serverJS.executeScript(script)  // 直接调用executeScript方法
                SparkCore.LOGGER.debug("脚本已执行: ${script.location}")
            } else {
                SparkCore.LOGGER.warn("服务器未启动，跳过脚本执行: ${script.location}")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("执行脚本失败: ${script.location}", e)
        }
    }

    private fun unloadScript(location: ResourceLocation, script: OJSScript) {
        try {
            // 使用单例的ServerSparkJS实现线程安全卸载
            val serverJS = getServerJSInstance()
            serverJS.unloadScript(script.apiId, script.fileName)
            SparkCore.LOGGER.debug("脚本已卸载: $location")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("卸载脚本失败: $location", e)
        }
    }

    private fun syncToClients(location: ResourceLocation, script: OJSScript, changeType: ChangeType) {
        try {
            JSIncrementalSyncS2CPacket.syncScriptToClients(
                location.namespace,
                script.apiId,
                script.fileName,
                script.content,
                changeType == ChangeType.MODIFIED
            )
            SparkCore.LOGGER.debug("脚本同步到客户端: $location ($changeType)")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("同步脚本到客户端失败: $location", e)
        }
    }

    // ===== 初始化 =====

    override fun initialize(modMainClass: Class<*>):Boolean {
        return try {
            // 发现资源路径
            val resourcePaths = ResourceDiscoveryService.discoverResourcePaths(resourceType)

            // 提取默认资源
            val extractionSuccess = extractDefaultResources(modMainClass)

            // 扫描并处理现有资源
            for (basePath in resourcePaths) {
                val resourceFiles = ResourceDiscoveryService.scanResourceFiles(basePath, supportedExtensions)

                for (resourceFile in resourceFiles) {
                    onResourceAdded(resourceFile)
                }
            }

            SparkCore.LOGGER.info("JavaScriptHandler 初始化完成，处理了 $processedCount 个脚本")
            extractionSuccess

        } catch (e: Exception) {
            SparkCore.LOGGER.error("JavaScriptHandler 初始化失败", e)
            false
        }
    }

    override fun initializeDefaultResources(modMainClass: Class<*>): Boolean {
        return extractDefaultResources(modMainClass)
    }

    private fun extractDefaultResources(modMainClass: Class<*>): Boolean {
        return MultiModuleResourceExtractionUtil.extractAllModuleResources(
            modMainClass,
            resourceType
        )
    }

    // ===== API ID 提取 =====

    /**
     * 从ResourceLocation路径中提取API ID
     * 路径格式: script/{api_id}/{fileName} 或 scripts/{api_id}/{fileName}
     * 如果无法提取，则使用默认值 "unknown"
     */
    private fun extractApiIdFromPath(location: ResourceLocation): String {
        val path = location.path

        // 处理四层目录结构: {moduleName}/scripts/{api_id}/{fileName}
        if (path.contains("/scripts/")) {
            val pathParts = path.split("/")
            val scriptsIndex = pathParts.indexOf("scripts")
            if (scriptsIndex >= 0 && scriptsIndex + 1 < pathParts.size) {
                return pathParts[scriptsIndex + 1] // 返回 scripts/ 后的第一个目录名
            }
        }

        // 处理四层目录结构: {moduleName}/script/{api_id}/{fileName} (单数形式)
        if (path.contains("/script/")) {
            val pathParts = path.split("/")
            val scriptIndex = pathParts.indexOf("script")
            if (scriptIndex >= 0 && scriptIndex + 1 < pathParts.size) {
                return pathParts[scriptIndex + 1] // 返回 script/ 后的第一个目录名
            }
        }

        // 处理传统格式: script/{api_id}/{fileName}
        if (path.startsWith("script/")) {
            val pathParts = path.split("/")
            if (pathParts.size >= 2) {
                return pathParts[1] // 返回 script/ 后的第一个目录名
            }
        }

        // 处理传统格式: scripts/{api_id}/{fileName} (复数形式)
        if (path.startsWith("scripts/")) {
            val pathParts = path.split("/")
            if (pathParts.size >= 2) {
                return pathParts[1] // 返回 scripts/ 后的第一个目录名
            }
        }

        // 如果无法提取，记录警告并返回默认值
        SparkCore.LOGGER.warn("无法从脚本路径提取API ID: ${location}, 使用默认值 'skill'")
        return "skill" // 默认使用skill API，因为大部分脚本都是技能脚本
    }

    // ===== 模块清理 =====

    override fun cleanupModuleResource(resourceLocation: ResourceLocation) {
        // 从Origins中移除
        val script = OJSScript.ORIGINS.remove(resourceLocation)

        // 卸载脚本
        if (script != null) {
            unloadScript(resourceLocation, script)
        }

        // 从注册表中移除
        if (isInitialScanComplete()) {
            unregisterFromRegistry(resourceLocation)
        }
    }


    /**
     * 清理资源，在Handler销毁时调用
     */
    override fun cleanup() {
        super.cleanup()
        try {
            // 清理ServerSparkJS实例
            serverJSInstance?.let { instance ->
                // 这里可以添加清理逻辑，例如关闭 Context
                SparkCore.LOGGER.debug("JavaScriptHandler 清理 ServerSparkJS 实例")
            }
            serverJSInstance = null
        } catch (e: Exception) {
            SparkCore.LOGGER.error("JavaScriptHandler 清理失败", e)
        }
    }
}