package cn.solarmoon.spark_core.js2.modules

import cn.solarmoon.spark_core.js2.JavaScript

interface JSModule {

    val id: String

    fun onInitialize() {}

    /**
     * 该模块下每加载完毕一个脚本时调用
     */
    fun onLoaded(script: JavaScript)

}