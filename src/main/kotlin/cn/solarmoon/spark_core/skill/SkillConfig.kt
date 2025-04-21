package cn.solarmoon.spark_core.skill

open class SkillConfig(
    val skill: Skill
): LinkedHashMap<String, Any>() {

    var ignoreAttackSpeed = false
    var canSweepAttack = true
    var canCriticalHit = true
    var canTargetKnockBack= true
    var damageMultiplier = 1f

    open fun init() {
        skill.onEvent<SkillEvent.PlayerGetAttackStrength> {
            if (ignoreAttackSpeed) it.event.attackStrengthScale = 1f
        }
        skill.onEvent<SkillEvent.SweepAttack> {
            if (!canSweepAttack) it.event.isSweeping = false
        }
        skill.onEvent<SkillEvent.CriticalHit> {
            if (!canCriticalHit) {
                if (it.event.vanillaMultiplier == 1.5f) it.event.isCriticalHit = false
            }
        }
        skill.onEvent<SkillEvent.TargetKnockBack> {
            if (!canTargetKnockBack) it.event.isCanceled = true
        }
        skill.onEvent<SkillEvent.TargetHurt> {
            if (damageMultiplier != 1f) it.event.container.newDamage *= damageMultiplier
        }
    }

}