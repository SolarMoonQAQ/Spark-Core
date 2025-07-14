package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.config.SparkConfig
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.presets.DynamicResourceApplier
import cn.solarmoon.spark_core.web.KtorWebServer
import cn.solarmoon.spark_core.web.logging.LogCollector
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent

/**
 * 资源系统注册中心
 * 负责协调资源处理器的初始化时序
 */
object SparkResourceRegister {

    /**
     * 1. 通用设置阶段 - 标记动态注册表的静态注册阶段完成
     * 必须在资源处理器初始化之前完成，以确保动态注册表准备就绪
     */
    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        SparkCore.LOGGER.info("通用设置阶段，标记动态注册表静态阶段完成...")
        markAllDynamicRegistriesStaticComplete()
    }

    /**
     * 2. 模组间通信阶段 - 初始化资源处理器
     * 此时动态注册表已准备就绪，可以安全地注册动态资源
     * 现在支持多mod资源提取，会自动处理所有已注册的mod
     */
    private fun onInterModEnqueue(event: InterModEnqueueEvent) {
        SparkCore.LOGGER.info("模组间通信阶段，初始化资源处理器...")
        HandlerDiscoveryService.discoverAndInitializeHandlers()
    }

    /**
     * 3. 服务器即将启动 - 启动热重载服务
     * 此时所有资源都已处理完成，可以启动热重载监控
     */
    private fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        SparkCore.LOGGER.info("服务器即将启动，启动热重载服务...")
        DynamicResourceApplier.onServerStart(event)

        // 启动Web服务器（如果启用）
        if (SparkConfig.COMMON_CONFIG.enableWebServer.get()) {
            SparkCore.LOGGER.info("初始化日志收集器...")
            // LogCollector不需要初始化，直接添加日志条目即可
            LogCollector.addLogEntry("INFO", "SparkCore", "日志收集器已启动")

            SparkCore.LOGGER.info("启动Web服务器...")
            KtorWebServer.start()
        }
    }

    /**
     * 预先标记所有动态注册表的静态阶段完成
     */
    private fun markAllDynamicRegistriesStaticComplete() {
        try {
            SparkRegistries.TYPED_ANIMATION.markStaticRegistrationComplete()
            SparkRegistries.MODELS.markStaticRegistrationComplete()
            SparkRegistries.DYNAMIC_TEXTURES.markStaticRegistrationComplete()
            SparkRegistries.JS_SCRIPTS.markStaticRegistrationComplete()
            SparkRegistries.IK_COMPONENT_TYPE.markStaticRegistrationComplete()
            SparkCore.LOGGER.info("所有动态注册表的静态阶段已标记完成")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("标记动态注册表静态阶段完成时出错", e)
        }
    }

    /**
     * 注册资源系统相关的事件监听器
     */
    @JvmStatic
    fun register(modEventBus: IEventBus) {
        // 注册mod事件总线监听器
        modEventBus.addListener(::onCommonSetup)
        modEventBus.addListener(::onInterModEnqueue)
        
        // 注册NeoForge事件总线监听器
        NeoForge.EVENT_BUS.addListener(::onServerAboutToStart)
        SparkCore.LOGGER.info("资源系统注册中心已注册")
    }
}
