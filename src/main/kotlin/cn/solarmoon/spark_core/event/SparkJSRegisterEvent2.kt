package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.JSApi
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class SparkJSRegisterEvent2(
    private val allApi: MutableSet<JSApi>
): Event(), IModBusEvent {

    fun register(api: JSApi) {
        allApi.add(api)
    }

}