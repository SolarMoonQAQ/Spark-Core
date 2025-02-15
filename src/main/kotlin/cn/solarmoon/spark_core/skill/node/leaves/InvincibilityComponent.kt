package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent

class InvincibilityComponent(
    val activeTime: List<Vec2> = listOf(),
    val onImmuneToDamage: BehaviorNode = EmptyNode.Success
): BehaviorNode() {

    private var status = NodeStatus.RUNNING

    init {
        dynamicContainer.addChild(onImmuneToDamage)
    }

    @SubscribeEvent
    private fun onHurt(event: LivingIncomingDamageEvent) {
        val victim = event.entity
        if (victim.level().isClientSide) return
        if (victim == skill.holder) {
            val time = require<() -> Double>("time").invoke()
            if (activeTime.isEmpty() || activeTime.any { time in it.x..it.y }) {
                write("on_hurt.time", time)
                event.source.directEntity?.let { write("attacker", it) }
                status = onImmuneToDamage.tick(skill)
                event.isCanceled = true
            }
        }
    }

    override fun onStart(skill: SkillInstance) {
        if (!skill.level.isClientSide) NeoForge.EVENT_BUS.register(this)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return status
    }

    override fun onEnd(skill: SkillInstance) {
        if (!skill.level.isClientSide) NeoForge.EVENT_BUS.unregister(this)
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return InvincibilityComponent(activeTime, onImmuneToDamage.copy())
    }

    companion object {
        val CODEC: MapCodec<InvincibilityComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime },
                BehaviorNode.CODEC.optionalFieldOf("on_immune_to_damage", EmptyNode.Success).forGetter { it.onImmuneToDamage },
            ).apply(it, ::InvincibilityComponent)
        }
    }

}