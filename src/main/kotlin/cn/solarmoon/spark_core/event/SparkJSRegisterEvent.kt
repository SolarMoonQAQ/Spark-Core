package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.put
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class SparkJSRegisterEvent(
    private val allApi: MutableSet<JSApi>
): Event(), IModBusEvent {

    fun register(api: JSApi) {
        allApi.add(api)
    }

}