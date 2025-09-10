package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.modules.JSModule
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.NeoForge
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

class SparkJS: TaskSubmitOffice {

    companion object {
        val LOGGER = SparkCore.logger("js脚本")

        private var serverInstance = SparkJS()
        private var clientInstance = SparkJS()

        fun get(isClientSide: Boolean) = if (isClientSide) clientInstance else serverInstance
    }

    private var v8Runtime: Context? = null
    private val inModules = mutableMapOf<String, JSModule>()
    private val inScripts = mutableMapOf<ResourceLocation, JavaScript>()
    private lateinit var boundThread: Thread
    override val taskMap: ConcurrentHashMap<PPhase, ConcurrentHashMap<String, () -> Unit>> = ConcurrentHashMap()
    override val immediateQueue: ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<() -> Unit>> = ConcurrentHashMap()

    val scripts get() = inScripts.toMap()
    val modules get() = inModules.toMap()
    val runtime get() = v8Runtime ?: throw NullPointerException("JS 尚未初始化")
    val isOnBoundThread get() = Thread.currentThread() == boundThread
    var isInitialized = false
        private set

    fun initialize() {
        isInitialized = false
        boundThread = Thread.currentThread()

        // 关闭旧实例
        v8Runtime?.close()

        // 创建新的 V8 Runtime
        v8Runtime = createContext()

        // 注册模块到 JS 全局
        NeoForge.EVENT_BUS.post(SparkJSRegisterEvent(inModules, runtime))
        inModules.values.forEach { it.onInitialize(runtime) }
        LOGGER.info("初始化完成")
        isInitialized = true
    }

    fun createContext() = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL) // 在 JS 中访问 Java 对象
        .build()

    fun load(script: JavaScript) {
        try {
            val src = Source.newBuilder("js", script.stringContent, script.index.toString())
                .encoding(StandardCharsets.UTF_8)
                .build()
            val value = v8Runtime!!.eval(src)
//            script.value = value
            inScripts[script.index] = script
            inModules[script.index.namespace]?.onLoaded(script)
        } catch (e: Exception) {
            LOGGER.error("加载脚本失败: ${script.index}", e)
        }
    }

    fun tickPre() {
        if (!isInitialized || !isOnBoundThread) return
        processTasks(PPhase.ALL)
        processTasks(PPhase.PRE)
    }

    fun tickPost() {
        if (!isInitialized || !isOnBoundThread) return
        processTasks(PPhase.ALL)
        processTasks(PPhase.POST)
    }

    fun getScript(index: ResourceLocation): JavaScript? {
        return inScripts[index]
    }

}