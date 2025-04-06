package cn.solarmoon.spark_core.js

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine

interface JSApi {

    val id: String

    val valueCache: MutableMap<String, String>

    fun onLoad()

    fun onRegister(engine: GraalJSScriptEngine)

    fun onReload()

}