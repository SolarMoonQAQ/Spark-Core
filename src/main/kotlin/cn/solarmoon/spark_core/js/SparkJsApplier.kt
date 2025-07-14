package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.ClientSparkJS
import cn.solarmoon.spark_core.js.ServerSparkJS
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import kotlin.collections.component1
import kotlin.collections.component2

object SparkJsApplier {

    @SubscribeEvent
    private fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as Level
        val js = level.jsEngine
        js.register()

        // 只注册JS引擎，不立即加载脚本
        if (js is ServerSparkJS) {
            SparkCore.LOGGER.info("服务端JS引擎已注册，等待服务器完全启动后加载脚本")
        } else {
            SparkCore.LOGGER.info("客户端JS引擎已注册，等待玩家登录后加载脚本")
        }
    }

    @SubscribeEvent
    private fun onServerStarted(event: ServerStartedEvent) {
        // 服务器完全启动后，所有mod的API都已注册完成
        val server = event.server
        for (level in server.allLevels) {
            val js = level.jsEngine
            if (js is ServerSparkJS) {
                SparkCore.LOGGER.info("服务器启动完成，开始加载服务端JS脚本")
                js.loadAllFromRegistry()
                break // 只需要加载一次
            }
        }
    }

    /**
     * 客户端玩家登录事件 - 这是客户端玩家完全准备好后的安全时机
     * 避免在配置阶段执行JS脚本导致网络同步错误
     */

    @SubscribeEvent
    private fun onClientPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        val level = event.player.level()
        SparkCore.LOGGER.info("Level: {}", level)
        val js: SparkJS = level.jsEngine
        
        SparkCore.LOGGER.info("客户端玩家登录完成，开始从注册表加载JS脚本")

        try {
            // 客户端玩家登录时，所有mod的API都已注册完成
            js.loadAllFromRegistry()
            // 兼容性处理：如果注册表为空，回退到传统的 clientApiCache 方式
            val registryScripts = SparkRegistries.JS_SCRIPTS.getDynamicEntries().size
            if (registryScripts == 0 && JSApi.clientApiCache.isNotEmpty()) {
                SparkCore.LOGGER.error("注册表为空, 检查脚本注册表是否正确")
            }
            SparkCore.LOGGER.info("客户端JS脚本加载完成")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("客户端JS脚本加载过程中发生错误", e)
        }
    }

}