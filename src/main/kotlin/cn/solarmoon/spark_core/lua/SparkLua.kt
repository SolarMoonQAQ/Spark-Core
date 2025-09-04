package cn.solarmoon.spark_core.lua

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.event.SparkLuaRegisterEvent
import cn.solarmoon.spark_core.js2.JavaScript
import cn.solarmoon.spark_core.js2.modules.JSModule
import cn.solarmoon.spark_core.lua.modules.LuaModule
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaStateFiveFour
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.NeoForge

object SparkLua {

    val LOGGER = SparkCore.logger("Lua脚本")

    private var inState: LuaStateFiveFour? = null
    private val inModules = mutableMapOf<String, LuaModule>()
    private val inScripts = mutableMapOf<ResourceLocation, LuaScript>()

    val scripts get() = inScripts.toMap()
    val modules get() = inModules.toMap()
    val state get() = inState ?: throw NullPointerException("Lua 尚未初始化")

    fun initialize() {
        inState?.close()
        inState = LuaStateFiveFour().apply {
            openLib(LuaState.Library.BASE)
            openLib(LuaState.Library.TABLE)
            openLib(LuaState.Library.STRING)
            openLib(LuaState.Library.MATH)
            openLib(LuaState.Library.DEBUG)
            openLib(LuaState.Library.ERIS)
            openLib(LuaState.Library.UTF8)
            openLib(LuaState.Library.JAVA)
        }
        NeoForge.EVENT_BUS.post(SparkLuaRegisterEvent(inModules, state))
        inModules.values.forEach { it.onInitialize() }
    }

    fun load(script: LuaScript) {
        state.load(script.stringContent, script.index.toString())
        state.call(0, 0)
    }

    fun getScript(index: ResourceLocation): LuaScript? {
        return inScripts[index]
    }

}