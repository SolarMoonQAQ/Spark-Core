package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.JSApi
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class SparkJSRegisterEvent(
    val engine: GraalJSScriptEngine,
    private val allApi: MutableSet<JSApi>
): Event(), IModBusEvent {

    fun register(api: JSApi) {
        allApi.add(api)
    }

}