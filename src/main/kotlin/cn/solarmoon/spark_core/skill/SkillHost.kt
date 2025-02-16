package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface SkillHost: Syncer {

    val skillCount: AtomicInteger

    val allSkills: ConcurrentHashMap<Int, SkillInstance>

    val predictedSkills: ConcurrentHashMap<Int, SkillInstance>

    val isPlayingSkill get() = allSkills.any { it.value.isActive }

    fun getSkill(id: Int) = allSkills[id]

}