package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent

class InvincibilityComponent(
    val activeTime: List<Vec2> = listOf(),
    val onImmuneToDamage: List<SkillComponent> = listOf(),
): SkillComponent() {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return InvincibilityComponent(activeTime, onImmuneToDamage.map { it.copy() })
    }

    @SubscribeEvent
    private fun onHurt(event: LivingIncomingDamageEvent) {
        val victim = event.entity
        if (victim.level().isClientSide) return
        if (victim == skill.holder) {
            val time = requireQuery<() -> Double>("time").invoke()
            if (activeTime.isEmpty() || activeTime.any { time in it.x..it.y }) {
                addOrReplaceContext("on_hurt.time") { time }
                setCustomActive(onImmuneToDamage)
                event.isCanceled = true
            }
        }
    }

    override fun onActive() {
        if (!skill.level.isClientSide) NeoForge.EVENT_BUS.register(this)
    }

    override fun onUpdate() {}

    override fun onEnd() {
        if (!skill.level.isClientSide) NeoForge.EVENT_BUS.unregister(this)
    }

    companion object {
        val CODEC: MapCodec<InvincibilityComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime },
                SkillComponent.CODEC.listOf().optionalFieldOf("on_immune_to_damage", listOf()).forGetter { it.onImmuneToDamage }
            ).apply(it, ::InvincibilityComponent)
        }
    }

}