package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.doc.LuaGlobal
import cn.solarmoon.spark_core.lua.execute
import cn.solarmoon.spark_core.skill.ScriptSource
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillManager
import cn.solarmoon.spark_core.skill.SkillStartCondition
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.skillType
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.resources.ResourceLocation

@LuaGlobal("Skill")
object LuaSkillGlobal {

    fun create(id: String, conditions: List<SkillStartCondition>, provider: LuaValueProxy) = skillType(ResourceLocation.parse(id), conditions) {
        onEvent<SkillEvent.Init> {
            provider.execute(this)
        }
    }.apply { fromScript = true }

    fun createBy(id: String, extend: String, conditions: List<SkillStartCondition>, provider: LuaValueProxy): SkillType<*> {
        val type = SkillManager.get(ResourceLocation.parse(extend)) ?: throw NullPointerException("父技能 $extend 无法被继承，因为此技能尚未注册")
        return skillType(ResourceLocation.parse(id), conditions.toMutableList().apply { addAll(type.conditions) }, { type.provider() }) {
            provider.execute(this)
        }.apply {
            fromScript = true
        }
    }

}

