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

        // 只在服务端执行脚本加载，客户端延迟到玩家登录后
        if (js is ServerSparkJS) {
            SparkCore.LOGGER.info("服务端JS引擎已注册，脚本将通过DynamicJavaScriptHandler热重载机制加载")
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
        val player = event.player
        val level = player.level()
        val js = level.jsEngine
        
        SparkCore.LOGGER.info("客户端玩家登录完成，开始加载JS脚本")
        
        // --- 新增：确保JS引擎已先初始化 Scope，使得后续 registerComponent 可以把对象注入其中 ---
        run {
            val ctx = org.mozilla.javascript.Context.enter()
            try {
                if (js.scope == null) {
                    js.context = ctx
                    js.scope = ctx.initStandardObjects()
                }
            } finally {
                org.mozilla.javascript.Context.exit()
            }
        }
        
        // 确保JS引擎重新注册API组件（防止注册时机问题）
        js.register()
        
        // 手动确保关键API组件已注入（避免事件总线时序问题）
        js.scope?.put("Skill", js.scope, cn.solarmoon.spark_core.js.skill.JSSkillApi)
        
        try {
            // 从动态注册表按API分组获取脚本
            val scriptsByApi = mutableMapOf<String, Map<String, String>>()
            SparkRegistries.JS_SCRIPTS?.getDynamicEntries()?.values?.groupBy { it.apiId }?.forEach { (apiId, scripts) ->
                scriptsByApi[apiId] = scripts.associate { it.fileName to it.content }
            }
            
            SparkCore.LOGGER.debug("客户端从动态注册表加载 ${scriptsByApi.size} 个API模块的脚本")
            
            // 按API分组执行脚本
            scriptsByApi.forEach { (apiId, scripts) ->
                try {
                    js.validateApi(apiId)
                    val api = JSApi.ALL[apiId]
                    if (api != null) {
                        scripts.forEach { (fileName, content) ->
                            js.eval(content, "$apiId - $fileName")
                        }
                        if (api is JSComponent) {
                            api.onLoad()
                        }
                        SparkCore.LOGGER.debug("客户端成功加载API模块: $apiId")
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("客户端JS脚本加载失败: $apiId", e)
                    throw e  // 重新抛出异常以便上层处理
                }
            }
            
            SparkCore.LOGGER.info("客户端JS脚本加载完成")
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("客户端JS脚本加载过程中发生错误", e)
        }
    }

}