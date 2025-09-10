package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.modules.JSModule
import net.neoforged.bus.api.Event
import org.graalvm.polyglot.Context

class SparkJSRegisterEvent(
    private val modules: MutableMap<String, JSModule>,
    val runtime: Context
): Event() {

    fun registerModule(module: JSModule) {
        modules[module.id] = module
    }

}