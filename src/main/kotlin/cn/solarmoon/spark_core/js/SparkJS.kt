package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.modules.JSModule
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModLoader
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.nio.charset.StandardCharsets

class SparkJS {
    companion object {
        val LOGGER = SparkCore.logger("js脚本")
        private val threadLocal = ThreadLocal.withInitial {
            SparkJS()
        }
        fun get() = threadLocal.get()
    }

    private var inContext: Context? = null
    private val inModules = mutableMapOf<String, JSModule>()
    private val inScripts = mutableMapOf<ResourceLocation, JavaScript>()

    val scripts get() = inScripts.toMap()
    val modules get() = inModules.toMap()
    val context get() = inContext ?: throw NullPointerException("JS 尚未初始化")
    lateinit var scriptable: Scriptable
        private set
    var isInitialized = false
        private set

    fun createContext() = Context.enter()

    fun initialize() {
        isInitialized = false
        inContext?.close()
        inContext = createContext()
        scriptable = context.initSafeStandardObjects()
        ModLoader.postEvent(SparkJSRegisterEvent(inModules, context, scriptable))
        inModules.values.forEach { it.onInitialize(scriptable) }
        LOGGER.info("初始化完成，已注册 ${inModules.size} 个模块: ${inModules.keys}")
        isInitialized = true
    }

    fun load(script: JavaScript) {
        try {
            context.evaluateString(scriptable, script.stringContent, script.index.toString(), 1, null)
            inScripts[script.index] = script
            inModules[script.index.namespace]?.onLoaded(script)
        } catch (e: Exception) {
            LOGGER.error("加载脚本失败: ${script.index}", e)
        }
    }

    fun getScript(index: ResourceLocation) = inScripts[index]

}
