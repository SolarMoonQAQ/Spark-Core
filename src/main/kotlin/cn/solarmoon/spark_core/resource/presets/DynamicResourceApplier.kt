package cn.solarmoon.spark_core.resource.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.IHotReloadSvcHolder
import cn.solarmoon.spark_core.resource.ResHotReloadService
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.handler.DynamicAnimationHandler 
import cn.solarmoon.spark_core.resource.handler.IDynamicResourceHandler
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent
import net.neoforged.neoforge.event.level.LevelEvent

@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object DynamicResourceApplier {

    @JvmStatic
    @SubscribeEvent
    fun onServerSetup(event: InterModEnqueueEvent) {
        SparkCore.LOGGER.info("FMLDedicatedServerSetupEvent received, attempting to initialize dynamic resource reloading.")
        try {
            val resHotReloadSvc = ResHotReloadService

            val handlers: List<IDynamicResourceHandler> = HandlerDiscoveryService.getDiscoveredHandlers()
            SparkCore.LOGGER.info("Discovered {} dynamic resource handler(s).", handlers.size)

            // 在注册目录和启动服务之前，标记所有相关注册表的静态注册阶段已完成。
            // 这确保了在 ResHotReloadService 的 processExistingFilesInDirectory (由 registerDirectory 触发)
            // 开始加载动态资源之前，注册表状态是正确的。
            handlers.filterIsInstance<DynamicAnimationHandler>().forEach { handler ->
                // DynamicAnimationHandler 中的 typedAnimationRegistry 应该是 internal 可访问的
                handler.typedAnimationRegistry.markStaticRegistrationComplete()
                SparkCore.LOGGER.info(
                    "已为 DynamicAnimationHandler (${handler.getResourceType()}) 的 typedAnimationRegistry 标记静态注册完成。"
                )
            }

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
                        handler.getDirectoryId(), // 在 catch 块中获取 ID 可能再次失败，但通常用于日志
                        e.message,
                        e
                    )
                }
            }

            // 所有 handler 的 registerDirectory 调用完成后，意味着初始文件扫描已完成
            // （因为 registerDirectory -> processExistingFilesInDirectory -> handler.onResourceAdded）
            // 此时，为 DynamicAnimationHandler 标记初始扫描已完成，以便后续的资源变动可以触发网络同步
            handlers.filterIsInstance<DynamicAnimationHandler>().forEach { animHandler ->
                animHandler.markInitialScanComplete()
                SparkCore.LOGGER.info("已为 DynamicAnimationHandler (${animHandler.getResourceType()}) 标记初始扫描完成。")
            }

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