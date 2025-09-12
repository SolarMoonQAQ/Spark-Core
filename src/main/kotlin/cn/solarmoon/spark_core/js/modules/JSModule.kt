package cn.solarmoon.spark_core.js.modules

import cn.solarmoon.spark_core.js.JavaScript
import org.mozilla.javascript.Scriptable

interface JSModule {

    val id: String

    fun onInitialize(context: Scriptable) {}

    /**
     * 该模块下每加载完毕一个脚本时调用
     */
    fun onLoaded(script: JavaScript)

}