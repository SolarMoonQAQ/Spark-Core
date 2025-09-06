package cn.solarmoon.spark_core.lua.modules

import cn.solarmoon.spark_core.lua.LuaScript

class DefaultLuaModule: LuaModule {

    companion object {
        const val ID = "default"
    }

    override val id: String = ID

    override fun onLoaded(script: LuaScript) {}

}