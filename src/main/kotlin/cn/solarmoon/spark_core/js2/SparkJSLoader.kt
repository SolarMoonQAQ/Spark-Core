package cn.solarmoon.spark_core.js2

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js2.modules.JSModule
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.NeoForge
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.charset.StandardCharsets
import kotlin.concurrent.getOrSet

class SparkJSLoader {

    companion object {
        val LOGGER = SparkCore.logger("JS脚本")

        private val loader = ThreadLocal<SparkJSLoader>()

        fun get() = loader.getOrSet { SparkJSLoader() }
    }

    lateinit var context: Context
        private set

    val bindings get() = context.getBindings("js")

    val isClientSide = Thread.currentThread().name.contains("Render")

    private val inModules = mutableMapOf<String, JSModule>()
    private val inScripts = mutableMapOf<ResourceLocation, JavaScript>()

    val scripts get() = inScripts.toMap()
    val modules get() = inModules.toMap()

    fun initialize() {
        inScripts.clear()
        if (::context.isInitialized) context.close()
        context = createContext()
        NeoForge.EVENT_BUS.post(SparkJSRegisterEvent(inModules, bindings))
        inModules.values.forEach { it.onInitialize() }
    }

    fun createContext() = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL) // 在 JS 中访问 Java 对象
        .allowExperimentalOptions(true)
        .option("engine.WarnInterpreterOnly", "false")
        .build()

    fun load(script: JavaScript) {
        try {
            val src = Source.newBuilder("js", script.stringContent, script.index.toString())
                .encoding(StandardCharsets.UTF_8)
                .build()
            val value = context.eval(src)
            script.value = value
            inScripts[script.index] = script
            inModules[script.index.namespace]?.onLoaded(script)
        } catch (e: Exception) {
            LOGGER.error("加载脚本失败: ${script.index}", e)
        }
    }

    fun getScript(index: ResourceLocation): JavaScript? {
        return inScripts[index]
    }

}