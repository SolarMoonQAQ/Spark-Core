package cn.solarmoon.spark_core.skill

open class DefaultSkillConfig: SkillConfig {

    override val storage: LinkedHashMap<String, Any> = linkedMapOf()

    override fun init(skill: Skill) {
        super.init(skill)
        skill.onEvent<SkillEvent.PlayerGetAttackStrength> {
            if (read("ignore_attack_speed", false)) it.event.attackStrengthScale = 1f
        }
        skill.onEvent<SkillEvent.SweepAttack> {
            if (!read("enable_sweep_attack", true)) it.event.isSweeping = false
        }
        skill.onEvent<SkillEvent.CriticalHit> {
            if (!read("enable_critical_hit", true)) {
                if (it.event.vanillaMultiplier == 1.5f) it.event.isCriticalHit = false
            }
        }
        skill.onEvent<SkillEvent.TargetKnockBack> {
            if (!read("enable_target_knockback", true)) it.event.isCanceled = true
        }
        skill.onEvent<SkillEvent.TargetKnockBack> {
            val origin = it.event.originalStrength.toDouble()
            val strength = read("target_knockback_strength", origin)
            if (strength != origin) it.event.strength = strength.toFloat()
        }
        skill.onEvent<SkillEvent.TargetHurt> {
            val damageMultiplier = read("damage_multiplier", 1.0)
            if (damageMultiplier != 1.0) it.event.container.newDamage *= damageMultiplier.toFloat()
        }
    }

}