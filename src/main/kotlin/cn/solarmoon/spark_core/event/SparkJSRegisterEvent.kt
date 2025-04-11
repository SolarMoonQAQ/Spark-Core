package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.SparkJS
import net.neoforged.bus.api.Event

class SparkJSRegisterEvent(
    val engine: SparkJS,
    private val allApi: MutableSet<JSApi>
): Event() {

    fun register(api: JSApi) {
        allApi.add(api)
    }

}