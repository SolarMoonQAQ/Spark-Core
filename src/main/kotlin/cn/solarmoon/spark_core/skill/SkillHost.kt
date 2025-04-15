package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.preinput.IPreInputHolder
import cn.solarmoon.spark_core.sync.Syncer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface SkillHost: Syncer, IPreInputHolder {

    val skillCount: AtomicInteger

    val allSkills: ConcurrentHashMap<Int, Skill>

    val predictedSkills: ConcurrentHashMap<Int, Skill>

    val activeSkills get() = allSkills.values.filter { it.isActivated }

    val isPlayingSkill get() = allSkills.any { it.value.isActivated }

    fun getSkill(id: Int) = allSkills[id]

}