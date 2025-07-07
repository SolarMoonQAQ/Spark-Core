package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import kotlin.collections.component1
import kotlin.collections.component2

object SparkJsApplier {

    @SubscribeEvent
    private fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as Level
        val js = level.jsEngine
        js.register()

        // 服务端和客户端执行不同的初始化逻辑
        if (js is ServerSparkJS) {
            SparkCore.LOGGER.info("服务端JS引擎已注册，开始加载脚本")
            // 从注册表加载并执行所有脚本
            js.loadAllFromRegistry()
        } else {
            SparkCore.LOGGER.info("客户端JS引擎已注册，等待玩家登录后加载脚本")
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
            // 从动态注册表加载脚本
            js.loadAllFromRegistry()
            
            // 兼容性处理：如果注册表为空，回退到传统的 clientApiCache 方式
            val registryScripts = SparkRegistries.JS_SCRIPTS?.getDynamicEntries()?.size ?: 0
            if (registryScripts == 0 && JSApi.clientApiCache.isNotEmpty()) {
                SparkCore.LOGGER.info("注册表为空，回退到传统加载方式")
                // 按API分组执行脚本
                JSApi.clientApiCache.forEach { (apiId, scripts) ->
                    try {
                        js.validateApi(apiId)
                        val api = JSApi.ALL[apiId]!!
                        scripts.forEach { (fileName, content) ->
                            js.eval(content, "$apiId - $fileName")
                        }
                        api.onLoad()
                        SparkCore.LOGGER.debug("客户端成功加载API模块: $apiId")
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("客户端JS脚本加载失败: $apiId", e)
                        throw e  // 重新抛出异常以便上层处理
                    }
                }
            }
            
            SparkCore.LOGGER.info("客户端JS脚本加载完成")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("客户端JS脚本加载过程中发生错误", e)
        }
    }

}