package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkLuaRegisterEvent
import cn.solarmoon.spark_core.lua.extensions.LuaAttackSystemGlobal
import cn.solarmoon.spark_core.lua.extensions.LuaSkillConditionGlobal
import cn.solarmoon.spark_core.lua.extensions.LuaSkillGlobal
import cn.solarmoon.spark_core.lua.modules.DefaultLuaModule
import cn.solarmoon.spark_core.lua.modules.SkillLuaModule
import cn.solarmoon.spark_core.lua.setGlobal
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge

object SparkLuaScriptRegister {

    private fun reg2(event: SparkLuaRegisterEvent) {
        event.registerModule(DefaultLuaModule())
        event.registerModule(SkillLuaModule())

        val state = event.state
        state.setGlobal("Skill", LuaSkillGlobal)
        state.setGlobal("SkillCondition", LuaSkillConditionGlobal)
        state.setGlobal("AttackSystem", LuaAttackSystemGlobal)
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        NeoForge.EVENT_BUS.addListener(::reg2)
    }

}