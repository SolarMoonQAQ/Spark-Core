package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

class VariableContext: IMolangContext {
    private var animatable: IAnimatable<*>? = null
    private var variables: MutableMap<String, Any> = mutableMapOf()
    override fun update(
        molang: String,
        anim: AnimInstance,
        context: Context,
        bindings: Value
    ) {
        this.animatable = anim.holder
        variables = this.animatable!!.variables
    }

    @HostAccess.Export
    fun getMember(name: String): Any = variables.getOrPut(name) { 0.0 }

    @HostAccess.Export
    fun setMember(name: String, value: Any) { animatable?.putVariable(name, value) }

}