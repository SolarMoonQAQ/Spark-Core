package cn.solarmoon.spark_core.resource.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.ResHotReloadService
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.common.IResourceHandler

import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent

/**
 * 动态资源应用器
 * 由模块事件总线注册，负责在服务器启动时初始化动态资源重载
 */
object DynamicResourceApplier {

    /**
     * 服务器即将启动时初始化动态资源重载
     * 由SparkCore主类注册到NeoForge事件总线
     */
    @JvmStatic
    fun onServerStart(event: ServerAboutToStartEvent) {
        SparkCore.LOGGER.info("FMLDedicatedServerSetupEvent received, attempting to initialize dynamic resource reloading.")
        try {
            val resHotReloadSvc = ResHotReloadService

            val handlers: List<IResourceHandler> = HandlerDiscoveryService.getDiscoveredHandlers()
            SparkCore.LOGGER.info("Discovered {} dynamic resource handler(s).", handlers.size)

            // 注意：动态注册表的静态阶段标记已在SparkCore.onCommonSetup()中完成
            // 这确保了在资源处理器初始化时，动态注册表已准备就绪

            if (handlers.isEmpty()) {
                SparkCore.LOGGER.warn("No dynamic resource handlers were discovered. ResHotReloadService will not be started if no handlers are registered.")
                // 如果没有处理器，后续逻辑会确保服务不会在没有注册目录的情况下启动
            }

            var successfullyRegisteredHandlers = 0
            handlers.forEach { handler ->
                try {
                    val directoryId = handler.getDirectoryId()
                    resHotReloadSvc.registerDirectory(directoryId, handler)
                    SparkCore.LOGGER.info(
                        "Successfully registered handler '{}' for directory ID: '{}' with ResHotReloadService.",
                        handler::class.simpleName,
                        directoryId
                    )
                    successfullyRegisteredHandlers++
                } catch (e: Exception) {
                    SparkCore.LOGGER.error(
                        "Failed to register handler '{}' for directory ID '{}': {}",
                        handler::class.simpleName,
                        handler.getDirectoryId(),
                        e.message,
                        e
                    )
                }
            }

            // 启动热重载服务
            if (successfullyRegisteredHandlers > 0 && !resHotReloadSvc.isMonitorActive) {
                try {
                    resHotReloadSvc.start()
                    SparkCore.LOGGER.info("ResHotReloadService started with {} successfully registered handler(s).", successfullyRegisteredHandlers)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Failed to start ResHotReloadService: {}", e.message, e)
                }
            } else {
                SparkCore.LOGGER.info("ResHotReloadService not started as no handlers were successfully registered or no directories were configured for monitoring.")
            }

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Failed to initialize or start dynamic resource reloading.", e)
        }
    }

}