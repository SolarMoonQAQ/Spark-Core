package cn.solarmoon.spark_core.lua.modules

import cn.solarmoon.spark_core.lua.LuaScript

interface LuaModule {

    val id: String

    fun onInitialize() {}

    /**
     * 该模块下每加载完毕一个脚本时调用
     */
    fun onLoaded(script: LuaScript)

}