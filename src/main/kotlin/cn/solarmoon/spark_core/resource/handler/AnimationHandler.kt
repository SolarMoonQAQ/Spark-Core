package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.sync.OAnimationSetSyncPayload
import cn.solarmoon.spark_core.registry.virtual.HotReloadRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.common.*
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil.normalizeResourceName
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistrationInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.io.path.readText

/**
 * 重构后的动画处理器
 * 使用统一的接口和服务，标准化路径解析和资源管理
 */
@AutoRegisterHandler
class AnimationHandler(
    private val typedAnimationRegistry: HotReloadRegistry<TypedAnimation>
) : ResourceHandlerBase() {

    companion object {
        init {
            HandlerDiscoveryService.registerHandler {
                AnimationHandler(cn.solarmoon.spark_core.registry.common.SparkRegistries.TYPED_ANIMATION)
            }
        }
    }

    private val resourceType = "animations"
    private val supportedExtensions = setOf("json")
    private var processedCount = 0

    init {
        SparkCore.LOGGER.info("AnimationHandler 初始化完成")
        // 注意：移除对DependencyGraph的注册，使用MetadataManager进行依赖管理
    }
    
    // ===== 基础接口实现 =====
    
    override fun getResourceType(): String = resourceType
    
    override fun getRegistryIdentifier(): ResourceLocation? = typedAnimationRegistry.key().location()
    
    override fun getSupportedExtensions(): Set<String> = supportedExtensions
    
    override fun getPriority(): Int = 10 // 较高优先级
    
    // 提供对注册表的访问 (for DynamicResourceApplier)
    val typedAnimationRegistryAccess: HotReloadRegistry<TypedAnimation>
        get() = this.typedAnimationRegistry
    
    // ===== 资源处理核心逻辑 =====
    
    override fun processResourceAdded(node: ResourceNode) {
        try {
            val jsonString = node.basePath.resolve(node.relativePath).readText()
            val jsonElement = JsonParser.parseString(jsonString)

            if (!jsonElement.isJsonObject) {
                throw ResourceHandlerException.ResourceParseException(node.id.toString())
            }

            // 解码动画集
            val animationSetResult = OAnimationSet.CODEC.decode(JsonOps.INSTANCE, jsonElement).orThrow
            val animationSet = animationSetResult.first

            // 存储到Origin映射
            OAnimationSet.ORIGINS[node.id] = animationSet

            // 规范化动画名称，确保符合ResourceLocation命名规范
            animationSet.animations = animationSet.animations.mapKeys { (key, _) ->
                normalizeResourceName(key)
            } as LinkedHashMap<String, OAnimation>

            // 增加处理计数
            processedCount++

            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 添加到模块资源映射
            addResourceToModule(moduleId, node.id)
            registerToRegistry(node.id, animationSet)
            // 如果初始扫描完成，注册到动态注册表
            if (isInitialScanComplete()) {
                // 发送网络同步
                PacketDistributor.sendToAllPlayers(OAnimationSetSyncPayload(node.id, animationSet))
            }

            SparkCore.LOGGER.debug("处理动画资源: ${node.id} (模块: $moduleId)")
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }
    
    override fun processResourceModified(node: ResourceNode) {
        val resourceId = node.id

        // 先清理旧的AnimIndex.ORIGINS映射（如果存在）
        val oldAnimationSet = OAnimationSet.ORIGINS[resourceId]
        if (oldAnimationSet != null) {
            clearAnimIndexMappings(resourceId, oldAnimationSet)
        }

        // 修改等同于重新添加
        processResourceAdded(node)
    }
    
    override fun processResourceRemoved(node: ResourceNode) {
        val resourceId = node.id
        // graph的节点删除已在基类中处理

        // 先检查资源是否存在，但不要立即删除
        val animationSet = OAnimationSet.ORIGINS[resourceId]
        if (animationSet != null) {
            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 从模块资源映射中移除
            removeResourceFromModule(moduleId, resourceId)

            // 先从注册表注销（需要访问ORIGINS中的数据）
            if (isInitialScanComplete()) {
                unregisterFromRegistry(resourceId)
            }

            // 最后从ORIGINS中移除
            OAnimationSet.ORIGINS.remove(resourceId)
            SparkCore.LOGGER.debug("动画集已从ORIGINS移除: $resourceId")
        } else {
            SparkCore.LOGGER.warn("无法注销动画集 $resourceId：在 ORIGINS 中未找到")
        }
    }
    
    // ===== 注册表操作 =====

    /**
     * 清理AnimIndex.ORIGINS中与指定动画集相关的映射
     */
    private fun clearAnimIndexMappings(location: ResourceLocation, animationSet: OAnimationSet) {
        val pathParts = location.path.split("/")
        if (pathParts.size >= 3) {
            val entityPath = pathParts[2]
            animationSet.animations.keys.forEach { animName ->
                val normalizedAnimName = normalizeResourceName(animName)
                val shortcutPath = ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "$entityPath/$normalizedAnimName"
                )
                AnimIndex.ORIGINS.remove(shortcutPath)
                SparkCore.LOGGER.debug("移除动画快捷映射: $shortcutPath")
            }
        }
    }

    private fun registerToRegistry(location: ResourceLocation, animationSet: OAnimationSet) {
        val work: () -> Unit = {
            try {
                // 为动画集中的每个动画创建 TypedAnimation 并注册
                animationSet.animations.forEach { (animName, _) ->
                    val normalizedAnimName = normalizeResourceName(animName)
                    val animIndex = AnimIndex(location, normalizedAnimName, useShortcutConversion = false)
                    val typedAnimation = TypedAnimation(animIndex) {}

                    val pathParts = location.path.split("/")
                    if (pathParts.size >= 3) {
                        val entityPath = pathParts[2]
                        val shortcutPath = ResourceLocation.fromNamespaceAndPath("minecraft", "$entityPath/$normalizedAnimName")
                        AnimIndex.ORIGINS[shortcutPath] = location
                        SparkCore.LOGGER.debug("添加动画快捷映射: $shortcutPath -> $location")
                    }

                    val flattenedPath = run {
                        val moduleName = pathParts[0]
                        val entityPath = pathParts[2]
                        "$moduleName/animations/$entityPath/$normalizedAnimName"
                    }
                    val animResourceLocation = ResourceLocation.fromNamespaceAndPath(location.namespace, flattenedPath)
                    val resourceKey = ResourceKey.create(typedAnimationRegistry.key(), animResourceLocation)
                    typedAnimationRegistry.register(resourceKey, typedAnimation, RegistrationInfo.BUILT_IN)
                    SparkCore.LOGGER.debug("动画已注册到注册表: $animResourceLocation (来自动画集: $location, 原始名称: $animName)")
                }
            } catch (e: Exception) {
                throw ResourceHandlerException.RegistryOperationException("REGISTER", location.toString(), e)
            }
        }
        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
        if (server != null) server.execute(work) else work.invoke()
    }

    private fun unregisterFromRegistry(location: ResourceLocation) {
        val work: () -> Unit = {
            try {
                val animationSet = OAnimationSet.ORIGINS[location]
                if (animationSet != null) {
                    clearAnimIndexMappings(location, animationSet)
                    animationSet.animations.forEach { (animName, _) ->
                        val normalizedAnimName = normalizeResourceName(animName)
                        val pathParts = location.path.split("/")
                        val flattenedPath = run {
                            val moduleName = pathParts[0]
                            val entityPath = pathParts[2]
                            "$moduleName/animations/$entityPath/$normalizedAnimName"
                        }
                        val animResourceLocation = ResourceLocation.fromNamespaceAndPath(location.namespace, flattenedPath)
                        val resourceKey = ResourceKey.create(typedAnimationRegistry.key(), animResourceLocation)
                        typedAnimationRegistry.unregisterDynamic(resourceKey)
                        SparkCore.LOGGER.debug("动画已从注册表注销: $animResourceLocation (来自动画集: $location, 原始名称: $animName)")
                    }
                } else {
                    SparkCore.LOGGER.warn("无法注销动画集 $location：在 ORIGINS 中未找到")
                }
            } catch (e: Exception) {
                throw ResourceHandlerException.RegistryOperationException("UNREGISTER", location.toString(), e)
            }
        }
        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
        if (server != null) server.execute(work) else work.invoke()
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
                    // 让基类处理
                    onResourceAdded(resourceFile)
                }
            }
            
            SparkCore.LOGGER.info("AnimationHandler 初始化完成，处理了 $processedCount 个动画集")
            extractionSuccess
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("AnimationHandler 初始化失败", e)
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
    
    // ===== 模块清理 =====
    
    override fun cleanupModuleResource(resourceLocation: ResourceLocation) {
        // 从Origins中移除
        OAnimationSet.ORIGINS.remove(resourceLocation)
        
        // 从注册表中移除
        if (isInitialScanComplete()) {
            unregisterFromRegistry(resourceLocation)
        }
    }
}
