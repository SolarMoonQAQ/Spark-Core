package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.node.BehaviorTree
import cn.solarmoon.spark_core.skill.node.NodeStatus
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.LevelTickEvent

class SkillInstance internal constructor(
    internal var id0: Int,
    val type: SkillType,
    val holder: SkillHost,
    val level: Level,
    val behaviorTree: BehaviorTree
) {

    val id get() = id0
    var isActive = false
        private set
    var runTime: Int = 0
        private set

    fun activate() {
        if (!isActive) {
            runTime = 0
            isActive = true
            behaviorTree.blackBoard["time"] = { runTime }
            NeoForge.EVENT_BUS.register(this)
        } else {
            SparkCore.LOGGER.warn("技能正在释放中，无法重复启用，请等待该技能释放完毕，或先结束该技能。")
        }
    }

    private fun update() {
        if (isActive) {
            when (behaviorTree.tick(this)) {
                NodeStatus.SUCCESS -> end()
                NodeStatus.FAILURE -> end()
                NodeStatus.RUNNING -> runTime++
            }
        }
    }

    fun end() {
        if (isActive) {
            isActive = false
            NeoForge.EVENT_BUS.unregister(this)
            level.submitImmediateTask {
                behaviorTree.end(this)
            }
        }
    }

    @SubscribeEvent
    private fun tick(event: LevelTickEvent.Pre) {
        update()
    }

}