package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.js.doc.JSClass
import cn.solarmoon.spark_core.preinput.PreInput

@JSClass("PreInput")
interface JSPreInput {

    val js_self get() = this as PreInput

    fun execute() {
        js_self.execute()
    }

    fun execute(consumer: () -> Unit) {
        js_self.execute(consumer)
    }

    fun executeIfPresent(id: Array<String>, consumer: () -> Unit) {
        js_self.executeIfPresent(*id) {
            consumer.invoke()
        }
    }

    fun executeExcept(id: Array<String>, consumer: () -> Unit) {
        js_self.executeExcept(*id) {
            consumer.invoke()
        }
    }

}