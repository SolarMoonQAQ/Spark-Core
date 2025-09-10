package cn.solarmoon.spark_core.js.modules

import cn.solarmoon.spark_core.js.JavaScript
import org.graalvm.polyglot.Context

interface JSModule {

    val id: String

    fun onInitialize(runtime: Context) {}

    /**
     * 该模块下每加载完毕一个脚本时调用
     */
    fun onLoaded(script: JavaScript)

}