package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.lua.extensions.LuaSkill;
import cn.solarmoon.spark_core.skill.Skill;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Skill.class)
public class SkillMixin implements LuaSkill {
}
