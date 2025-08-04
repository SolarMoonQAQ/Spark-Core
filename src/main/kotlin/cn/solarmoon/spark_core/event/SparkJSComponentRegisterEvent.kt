package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.SparkJS
import net.neoforged.bus.api.Event

class SparkJSComponentRegisterEvent(
    val engine: SparkJS
): Event() {

    fun registerComponent(name: String, component: JSComponent) {
        component.engine = engine
        engine.scope?.put(name, engine.scope, component)
    }

}