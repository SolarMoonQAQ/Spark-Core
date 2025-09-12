package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.modules.JSModule
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

class SparkJSRegisterEvent(
    private val modules: MutableMap<String, JSModule>,
    val context: Context,
    val scriptable: Scriptable
): Event(), IModBusEvent {

    fun registerModule(module: JSModule) {
        modules[module.id] = module
    }

}