package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.anim.AnimInstance
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

interface IMolangContext {
    fun update(
        molang: String,
        anim: AnimInstance,
        context: Context,
        bindings: Value
    )
}