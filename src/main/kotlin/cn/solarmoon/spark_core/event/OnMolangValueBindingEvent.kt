package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import net.neoforged.bus.api.Event
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

class OnMolangValueBindingEvent(
    val value: JSMolangValue,
    val animatable: IAnimatable<*>,
    val context: Context,
    val bindings: Value
): Event() {
}