package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.component.SkillComponent
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.LevelTickEvent

class SkillInstance internal constructor(
    var id: Int,
    val type: SkillType,
    val holder: SkillHost,
    val level: Level,
    val components: List<SkillComponent>
) {

    var isActive = false
        private set
    var runTime: Int = 0
        private set

    init {
        components.forEachIndexed { index, component ->
            component.ordinal = index
            component.skill = this
            component.init()
        }
    }

    fun activate() {
        if (!isActive) {
            runTime = 0
            isActive = true
            components.forEach { it.onActive() }
        } else {
            SparkCore.LOGGER.warn("技能正在释放中，无法重复启用，请等待该技能释放完毕，或先结束该技能。")
        }
    }

    fun update() {
        if (isActive) {
            components.forEach { it.onUpdate() }
        }
    }

    fun end() {
        if (isActive) {
            isActive = false
            if (id < 0) holder.predictedSkills.remove(id)
            else holder.allSkills.remove(id)
            components.forEach { it.onEnd() }
        }
    }

}