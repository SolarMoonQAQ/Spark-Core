package cn.solarmoon.spark_core.js

import org.graalvm.polyglot.Context

private val context = ThreadLocal.withInitial {
    Context.newBuilder("js")
        .allowAllAccess(true)
        .build()
}
fun safeGetOrCreateJSContext() = context.get()

fun Context.getJSBindings() = getBindings("js")

fun Context.eval(code: CharSequence) = eval("js", code)
