package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.skill.component.SkillComponent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.LevelTickEvent

class SkillInstance internal constructor(
    val type: SkillType,
    val holder: SkillHost,
    val components: List<SkillComponent> = listOf<SkillComponent>()
) {

    val context = Context()
    var isActive = false
        private set
    var runTime: Int = 0
        private set

    fun activate() {
        if (!isActive) {
            for (i in 0 until components.size) {
                if (!components[i].onActive(this)) {
                    return
                }
            }

            holder.activeSkills.add(this)
            isActive = true
            NeoForge.EVENT_BUS.register(this)
        } else {
            SparkCore.LOGGER.warn("技能正在释放中，无法重复启用，请等待该技能释放完毕，或先结束该技能。")
        }
    }

    @SubscribeEvent
    private fun tick(event: LevelTickEvent.Pre) {
        update()
    }

    private fun update() {
        if (isActive) {
            runTime++
            for (i in 0 until components.size) {
                if (!components[i].onUpdate(this)) {
                    break
                }
            }
        }
    }

    fun end() {
        if (isActive) {
            runTime = 0
            isActive = false
            for (i in 0 until components.size) {
                if (!components[i].onStop(this)) {
                    break
                }
            }

            holder.activeSkills.remove(this)
            NeoForge.EVENT_BUS.unregister(this)
        }
    }

    class Context {
        var animation: AnimInstance? = null
    }

}