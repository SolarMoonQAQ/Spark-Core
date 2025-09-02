package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js2.modules.JSModule
import net.neoforged.bus.api.Event
import org.graalvm.polyglot.Value

class SparkJSRegisterEvent(
    private val modules: MutableMap<String, JSModule>,
    val bindings: Value
): Event() {

    fun registerModule(module: JSModule) {
        modules[module.id] = module
    }

}