package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.extension.JSSkill;
import cn.solarmoon.spark_core.skill.Skill;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Skill.class)
public class SkillMixin implements JSSkill {
}
