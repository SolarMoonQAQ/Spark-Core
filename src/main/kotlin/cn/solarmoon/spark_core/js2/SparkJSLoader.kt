package cn.solarmoon.spark_core.js2

import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js2.modules.JSModule
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.NeoForge
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import java.nio.charset.StandardCharsets

object SparkJSLoader {

    val context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL) // 在 JS 中访问 Java 对象
        .build()

    val bindings get() = context.getBindings("js")

    private val inModules = mutableMapOf<String, JSModule>()
    private val inScripts = mutableMapOf<ResourceLocation, JavaScript>()

    val scripts get() = inScripts.toMap()
    val modules get() = inModules.toMap()

    fun initialize() {
        inScripts.clear()
        NeoForge.EVENT_BUS.post(SparkJSRegisterEvent(inModules, bindings))
    }

    fun load(script: JavaScript) {
        val src = Source.newBuilder("js", script.stringContent, script.index.toString())
            .encoding(StandardCharsets.UTF_8)
            .build()
        val value = context.eval(src)
        script.value = value
        inScripts[script.index] = script
        inModules[script.index.namespace]?.onLoaded(script)
    }

    fun getScript(index: ResourceLocation): JavaScript? {
        return inScripts[index]
    }

}