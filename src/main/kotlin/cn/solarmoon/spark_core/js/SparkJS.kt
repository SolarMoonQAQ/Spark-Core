package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSComponentRegisterEvent
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.js.skill.JSSkillApi
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.neoforged.fml.util.LoaderException
import net.neoforged.neoforge.common.NeoForge
import org.mozilla.javascript.Context

abstract class SparkJS {

    val context = Context.enter()

    val scope = context.initStandardObjects()

    fun register() {
        NeoForge.EVENT_BUS.post(SparkJSComponentRegisterEvent(this))
    }

    fun eval(script: String, contextName: String = "") {
        runCatching {
            context.evaluateString(scope, script, contextName, 1, null)
        }.getOrElse { throw LoaderException("Js脚本加载失败: $contextName", it) }
    }

    fun validateApi(api: String) {
        if (!JSApi.ALL.containsKey(api)) throw NullPointerException("JS API $api 尚未注册！")
    }

    fun validateApi(api: JSApi) = validateApi(api.id)

    /**
     * 从动态注册表加载并执行所有脚本
     */
    open fun loadAllFromRegistry() {
        try {
            val scripts = SparkRegistries.JS_SCRIPTS.getDynamicEntries()
            SparkCore.LOGGER.info("开始从注册表加载 ${scripts.size} 个JS脚本")
            
            // 按 API 分组脚本
            val scriptsByApi = scripts.values.groupBy { it.apiId }
            
            scriptsByApi.forEach { (apiId, apiScripts) ->
                try {
                    validateApi(apiId)
                    val api = JSApi.ALL[apiId]!!
                    
                    // 执行该 API 的所有脚本
                    apiScripts.forEach { script ->
                        executeScript(script)
                    }
                    
                    // 调用 API 的 onLoad 钩子
                    api.onLoad()
                    SparkCore.LOGGER.info("成功加载API模块: $apiId (${apiScripts.size} 个脚本)")
                    
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("加载API模块失败: $apiId", e)
                }
            }
            
            SparkCore.LOGGER.info("JS脚本加载完成")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("从注册表加载脚本时发生错误", e)
        }
    }

    /**
     * 重新加载单个脚本
     */
    open fun reloadScript(script: OJSScript) {
        try {
            validateApi(script.apiId)
            executeScript(script)
            
            val api = JSApi.ALL[script.apiId]!!
            api.onLoad()
            
            SparkCore.LOGGER.info("重新加载脚本: API=${script.apiId}, 文件=${script.fileName}")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("重新加载脚本失败: API=${script.apiId}, 文件=${script.fileName}", e)
        }
    }

    /**
     * 卸载脚本（移除其产生的副作用）
     */
    open fun unloadScript(apiId: String, fileName: String) {
        try {
            validateApi(apiId)
            val api = JSApi.ALL[apiId]!!

            // 对于JSSkillApi，使用精确卸载
            if (api is JSSkillApi) {
                api.unloadScript(fileName)
            } else {
                // 其他API仍使用全局重载
                api.onReload()
            }

            SparkCore.LOGGER.info("卸载脚本: API=$apiId, 文件=$fileName")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("卸载脚本失败: API=$apiId, 文件=$fileName", e)
        }
    }

    /**
     * 执行单个脚本，子类可以重写以实现线程安全
     * 公共方法，可以从外部调用
     */
    open fun executeScript(script: OJSScript) {
        // 设置当前文件名到对应的API
        val api = JSApi.ALL[script.apiId]
        if (api is JSSkillApi) {
            api.currentFileName = script.fileName
        }

        try {
            eval(script.content, "${script.apiId} - ${script.fileName}")
        } finally {
            // 清除当前文件名
            if (api is JSSkillApi) {
                api.currentFileName = null
            }
        }
    }

}