package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.lua.modules.LuaModule
import li.cil.repack.com.naef.jnlua.LuaState
import net.neoforged.bus.api.Event

class SparkLuaRegisterEvent(
    private val modules: MutableMap<String, LuaModule>,
    val state: LuaState
): Event() {

    fun registerModule(module: LuaModule) {
        modules[module.id] = module
    }

}