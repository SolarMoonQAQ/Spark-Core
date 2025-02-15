package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.common.NeoForge

class PreventLocalInputComponent(
    val activeTime: List<Vec2> = listOf(),
): BehaviorNode() {

    val playerMove = { event: MovementInputUpdateEvent ->
        run {
            val player = event.entity
            val input = event.input
            val time = require<() -> Double>("time").invoke()
            if (activeTime.isEmpty() || activeTime.any { time in it.x..it.y }) {
                input.forwardImpulse = 0f
                input.leftImpulse = 0f
                input.up = false
                input.down = false
                input.left = false
                input.right = false
                input.jumping = false
                input.shiftKeyDown = false
                (player as? LocalPlayer)?.sprintTriggerTime = -1
                player.swinging = false
            }
        }
    }

    override fun onStart(skill: SkillInstance) {
        if (skill.level.isClientSide) {
            NeoForge.EVENT_BUS.addListener(playerMove)
        }
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return NodeStatus.SUCCESS
    }

    override fun onEnd(skill: SkillInstance) {
        if (skill.level.isClientSide) {
            NeoForge.EVENT_BUS.unregister(playerMove)
        }
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return PreventLocalInputComponent(activeTime)
    }

    companion object {
        val CODEC: MapCodec<PreventLocalInputComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventLocalInputComponent)
        }
    }

}