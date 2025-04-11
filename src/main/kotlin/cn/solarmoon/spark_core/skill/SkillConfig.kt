package cn.solarmoon.spark_core.skill

class SkillConfig(
    val skill: Skill
) {

    /**
     * 使得玩家伤害不受原版攻速影响，总是打满所有伤害
     */
    fun ignoreAttackSpeed() {
        skill.onEvent<SkillEvent.PlayerGetAttackStrength> {
            it.event.attackStrengthScale = 1f
        }
    }

    /**
     * 玩家伤害不会触发横扫攻击
     */
    fun disableSweepAttack() {
        skill.onEvent<SkillEvent.SweepAttack> {
            it.event.isSweeping = false
        }
    }

    /**
     * 玩家伤害不会触发原版暴击（但不会影响别的模组导致的暴击）
     */
    fun disableCriticalHit() {
        skill.onEvent<SkillEvent.CriticalHit> {
            if (it.event.vanillaMultiplier == 1.5f) it.event.isCriticalHit = false
        }
    }

    /**
     * 设置伤害倍率
     */
    fun setDamageMultiplier(mul: Float) {
        skill.onEvent<SkillEvent.TargetHurt> {
            it.event.container.newDamage *= mul
        }
    }

}