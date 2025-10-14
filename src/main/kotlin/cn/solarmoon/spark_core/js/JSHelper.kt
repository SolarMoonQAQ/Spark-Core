package cn.solarmoon.spark_core.js

import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

fun Scriptable.put(name: String, value: Any) = put(name, this, Context.javaToJS(value, this))

fun Scriptable.getMember(name: String) = ScriptableObject.getProperty(this, name) as? Scriptable

fun Scriptable.getFunctionMember(name: String) = getMember(name) as? Function

fun Function.execute(vararg args: Any?) = call(SparkJS.get().context, SparkJS.get().scriptable, SparkJS.get().scriptable, args)

private val context = ThreadLocal.withInitial {
    org.graalvm.polyglot.Context.newBuilder("js")
        .allowAllAccess(true)
        .build()
}
fun safeGetOrCreateJSContext() = context.get()

fun org.graalvm.polyglot.Context.getJSBindings() = getBindings("js")

fun org.graalvm.polyglot.Context.eval(code: CharSequence) = eval("js", code)
