package cn.solarmoon.spark_core.resource.autoregistry

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.resource.ResourceExtractionCompletionTracker
import cn.solarmoon.spark_core.resource.common.IResourceHandler
import cn.solarmoon.spark_core.resource.common.ModResourceInfo
import cn.solarmoon.spark_core.resource.common.MultiModResourceRegistry
import cn.solarmoon.spark_core.resource.discovery.ResourceDiscoveryService
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.handler.*

/**
 * 处理器发现服务
 * 由SparkCore主类的模块事件总线注册
 * 支持自动注册机制，handler类可以通过registerHandler方法自动注册
 */
object HandlerDiscoveryService {

    private val discoveredHandlers = mutableListOf<IResourceHandler>()
    private val registeredHandlerFactories = mutableListOf<() -> IResourceHandler>()

    /**
     * 注册handler工厂方法
     * 供handler类在静态初始化时调用，实现自动注册
     * @param factory handler实例化工厂方法
     */
    @JvmStatic
    fun registerHandler(factory: () -> IResourceHandler) {
        registeredHandlerFactories.add(factory)
        SparkCore.LOGGER.debug("注册handler工厂: {}", factory.javaClass.simpleName)
    }

    private fun findHandlerImplementations(): List<IResourceHandler> {
        // 优先使用自动注册的handlers
        val autoRegisteredHandlers = registeredHandlerFactories.mapNotNull { factory ->
            try {
                factory()
            } catch (e: Exception) {
                SparkCore.LOGGER.error("创建自动注册的handler失败", e)
                null
            }
        }

        // 如果有自动注册的handlers，使用它们
        if (autoRegisteredHandlers.isNotEmpty()) {
            SparkCore.LOGGER.info("使用自动注册的handlers: {} 个", autoRegisteredHandlers.size)
            return autoRegisteredHandlers
        }

        // Fallback到硬编码方式（向后兼容）
        SparkCore.LOGGER.warn("未发现自动注册的handlers，使用硬编码fallback")
        return listOf(
            AnimationHandler(SparkRegistries.TYPED_ANIMATION),
            ModelHandler(SparkRegistries.MODELS),
            TextureHandler(SparkRegistries.DYNAMIC_TEXTURES),
            JavaScriptHandler(SparkRegistries.JS_SCRIPTS),
            IKConstraintHandler(SparkRegistries.IK_COMPONENT_TYPE),
            MetaHandler()
        )
    }

    /**
     * 获取所有已注册的模组资源信息
     *
     * @return 已注册模组信息的不可变列表
     */
    fun getRegisteredMods(): List<ModResourceInfo> {
        return MultiModResourceRegistry.getRegisteredMods()
    }

    fun discoverAndInitializeHandlers() {
        val handlers = findHandlerImplementations()
        discoveredHandlers.addAll(handlers)
        SparkCore.LOGGER.info("已发现/正在使用 {} 个动态资源处理程序。", handlers.size)

        // 获取所有已注册的mod
        val registeredMods = getRegisteredMods()
        SparkCore.LOGGER.info("发现 {} 个已注册的mod，开始为每个mod提取资源", registeredMods.size)

        // 注册预期的处理器数量（处理器数量 × mod数量）
        val totalExtractionTasks = handlers.size * registeredMods.size
        ResourceExtractionCompletionTracker.registerExpectedHandlers(totalExtractionTasks)

        // 为每个mod和每个处理器组合进行资源提取
        for (modInfo in registeredMods) {
            SparkCore.LOGGER.info("开始为mod {} 提取资源", modInfo.modId)

            for (handler in discoveredHandlers) {
                SparkCore.LOGGER.info("正在为mod {} 使用handler {} 提取资源", modInfo.modId, handler::class.simpleName)
                try {
                    handler.initializeDefaultResources(modInfo.modMainClass)
                    // 标记该处理器的资源提取完成
                    ResourceExtractionCompletionTracker.markHandlerExtractionComplete("${modInfo.modId}-${handler::class.simpleName ?: "Unknown"}")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error(
                        "为mod {} 使用handler {} 初始化资源时出现异常: {}",
                        modInfo.modId,
                        handler::class.simpleName,
                        e.message,
                        e
                    )
                    // 即使失败也要标记完成，防止阻塞
                    ResourceExtractionCompletionTracker.markHandlerExtractionComplete("${modInfo.modId}-${handler::class.simpleName ?: "Unknown"}")
                }
            }

            SparkCore.LOGGER.info("完成为mod {} 提取资源", modInfo.modId)
        }
        // 在所有处理器都注册后，开始初始化流程
        // 注意：这里的初始化顺序可能很重要
        ResourceExtractionCompletionTracker.onExtractionComplete {
            // 第一步：初始化资源发现服务
            SparkCore.LOGGER.info("初始化资源发现服务...")
            ResourceDiscoveryService.initialize()

            // 第二步：初始化所有处理器
            for (handler in discoveredHandlers) {
                try {
                    handler.initialize(this.javaClass)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("初始化处理器失败: ${handler.javaClass.simpleName}", e)
                }
            }

            // 第三步：初始化依赖系统
            // 所有资源节点加载完成后，开始计算依赖关系
            try {
                cn.solarmoon.spark_core.resource.graph.ResourceGraphManager.initializeDependencySystem()
                SparkCore.LOGGER.info("依赖系统初始化完成，所有资源依赖关系已建立")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("依赖系统初始化失败", e)
            }

            // 第四步：标记所有处理器的初始扫描完成
            for (handler in discoveredHandlers) {
                handler.markInitialScanComplete()
            }

            // 第五步：初始化覆盖系统
            // 等待所有资源和依赖关系加载完成后，启动覆盖系统
            try {
                ResourceGraphManager.initializeOverrideSystem()
                SparkCore.LOGGER.info("覆盖系统初始化完成，现在支持热重载时的自动覆盖更新")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("覆盖系统初始化失败", e)
            }
        }
    }

    fun getDiscoveredHandlers(): List<IResourceHandler> = discoveredHandlers.toList()
}
