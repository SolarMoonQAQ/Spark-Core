package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.camera.CameraAdjuster
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.phys.Vec2

class PreventYRotComponent(
    val activeTime: List<Vec2> = listOf()
): BehaviorNode() {

    override fun onStart(skill: SkillInstance) {
        if (skill.level.isClientSide && activeTime.isEmpty()) CameraAdjuster.isActive = true
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        if (activeTime.isNotEmpty()) {
            val time = require<() -> Double>("time").invoke()
            CameraAdjuster.isActive = activeTime.any { time in it.x..it.y }
        }
        return NodeStatus.RUNNING
    }

    override fun onEnd(skill: SkillInstance) {
        if (skill.level.isClientSide) CameraAdjuster.isActive = false
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return PreventYRotComponent(activeTime)
    }

    companion object {
        val CODEC: MapCodec<PreventYRotComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventYRotComponent)
        }
    }

}