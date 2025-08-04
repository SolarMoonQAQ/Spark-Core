package cn.solarmoon.spark_core.skill

open class SkillConfig(
    val skill: Skill
) {

    val storage = linkedMapOf<String, Any>()

    open fun init() {
        skill.onEvent<SkillEvent.PlayerGetAttackStrength> {
            if (read("ignore_attack_speed", false)) it.event.attackStrengthScale = 1f
        }
        skill.onEvent<SkillEvent.SweepAttack> {
            if (!read("can_sweep_attack", true)) it.event.isSweeping = false
        }
        skill.onEvent<SkillEvent.CriticalHit> {
            if (!read("can_critical_hit", true)) {
                if (it.event.vanillaMultiplier == 1.5f) it.event.isCriticalHit = false
            }
        }
        skill.onEvent<SkillEvent.TargetKnockBack> {
            if (!read("can_target_knockback", true)) it.event.isCanceled = true
        }
        skill.onEvent<SkillEvent.TargetHurt> {
            val damageMultiplier = read("damage_multiplier", 1f)
            if (damageMultiplier != 1f) it.event.container.newDamage *= damageMultiplier
        }
    }

    fun put(id: String, value: Any) {
        storage[id] = value
    }

    inline fun <reified T: Any> read(key: String, defaultValue: T): T {
        val value = storage.get(key)
        if (value == null) return defaultValue
        return when (value) {
            is T -> value
            else -> throw IllegalArgumentException("技能配置参数[$key] 的类型必须为 ${T::class.simpleName}")
        }
    }

}