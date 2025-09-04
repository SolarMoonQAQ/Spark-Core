package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.lua.doc.LuaGlobal
import cn.solarmoon.spark_core.skill.SkillStartCondition
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.world.entity.Entity

@LuaGlobal
object LuaSkillConditionGlobal {

    fun create(name: String, reason: String, condition: LuaValueProxy): SkillStartCondition {
        return SkillStartCondition(name, reason) { holder, level ->
            condition.pushValue()
            condition.luaState.pushJavaObject(holder)
            condition.luaState.pushJavaObject(level)
            condition.luaState.call(2, 1)
            val result = condition.luaState.toBoolean(-1)
            condition.luaState.pop(1)
            result
        }
    }

    fun isEntity() = SkillStartCondition("isEntity", "技能持有者必须为实体") { holder, level -> holder is Entity }

    fun isAnimatable() = SkillStartCondition("isAnimatable", "技能持有者必须为动画体") { holder, level -> holder is IAnimatable<*> }

}