package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.js.molang.IMolangContext
import net.neoforged.bus.api.Event

class MolangRegisterEvent(
    private val contexts: MutableMap<String, IMolangContext>
): Event() {
    fun register(name: String, context: IMolangContext) {
        contexts[name] = context
    }
}