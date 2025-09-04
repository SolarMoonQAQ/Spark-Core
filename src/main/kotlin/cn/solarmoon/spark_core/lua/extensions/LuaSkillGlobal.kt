package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.doc.LuaGlobal
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillStartCondition
import cn.solarmoon.spark_core.skill.skillType
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.resources.ResourceLocation

@LuaGlobal
object LuaSkillGlobal {

    fun create(id: String, conditions: List<SkillStartCondition>, provider: LuaValueProxy) {
        skillType(ResourceLocation.parse(id), conditions) {
            onEvent<SkillEvent.Init> {
                provider.pushValue()
                provider.luaState.pushJavaObject(this)
                provider.luaState.call(1, 0)
            }
        }.apply { fromScript = true }
    }

}

