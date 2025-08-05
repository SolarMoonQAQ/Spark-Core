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

    fun set(id: String, value: Any) {
        storage[id] = value
    }

    inline fun <reified T: Any> read(key: String, defaultValue: T): T {
        val value = storage.get(key)
        if (defaultValue is Number && defaultValue !is Double) throw IllegalArgumentException("由于js的数字类型限制为double，因此配置参数[$key] 的类型不能为 ${defaultValue::class.simpleName}，请用double类型填写然后转为想要的类型。")
        return when (value) {
            null -> defaultValue
            is T -> value
            else -> throw IllegalArgumentException("技能配置参数[$key] 的类型必须为 ${T::class.simpleName}")
        }
    }

    inline fun <reified T: Any> readNonNull(key: String): T {
        val value = storage[key]
        if (value == null) throw IllegalArgumentException("技能配置参数[$key] 不能为空")
        if (value !is T) throw IllegalArgumentException("技能配置参数[$key] 的类型必须为 ${T::class.simpleName}")
        return value
    }

}