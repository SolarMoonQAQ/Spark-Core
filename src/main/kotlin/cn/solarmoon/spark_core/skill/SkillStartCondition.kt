package cn.solarmoon.spark_core.skill

import net.minecraft.world.level.Level

class SkillStartCondition(
    val id: String,
    val reason: String = "",
    val check: (SkillHost, Level) -> Boolean = { _, _ -> true },
) {
    @Throws(SkillStartRejectedException::class)
    fun test(host: SkillHost, level: Level) {
        if (!check(host, level)) {
            throw SkillStartRejectedException(reason)
        }
    }
}

class SkillStartRejectedException(val reason: String) : Exception(reason)