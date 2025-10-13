package cn.solarmoon.spark_core.js.molang

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess

private val context = ThreadLocal.withInitial {
    Context.newBuilder("js")
        .allowHostAccess(HostAccess.EXPLICIT)
        .build()
}

fun getMolangJSContext() = context.get()