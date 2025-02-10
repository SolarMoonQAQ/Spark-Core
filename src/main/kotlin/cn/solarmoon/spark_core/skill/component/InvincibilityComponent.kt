package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent

class InvincibilityComponent(
    val activeTime: List<Vec2> = listOf(),
    val onImmuneToDamage: List<SkillComponent> = listOf(),
    children: List<SkillComponent> = listOf()
): SkillComponent(children) {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return InvincibilityComponent(activeTime, onImmuneToDamage.map { it.copy() }, children)
    }

    @SubscribeEvent
    private fun onHurt(event: LivingIncomingDamageEvent) {
        val victim = event.entity
        if (victim.level().isClientSide) return
        if (victim == skill.holder) {
            val time = query<AnimInstance>("animation")?.time ?: skill.runTime.toDouble()
            if (activeTime.isEmpty() || activeTime.any { time in it.x..it.y }) {
                onImmuneToDamage.forEach {
                    addContext("on_hurt.time", time)
                    it.active(skill)
                }
                event.isCanceled = true
            }
        }
    }

    override fun onActive(): Boolean {
        if (!skill.level.isClientSide) NeoForge.EVENT_BUS.register(this)
        return true
    }

    override fun onUpdate(): Boolean {
        return true
    }

    override fun onEnd(): Boolean {
        if (!skill.level.isClientSide) NeoForge.EVENT_BUS.unregister(this)
        return true
    }

    companion object {
        val CODEC: MapCodec<InvincibilityComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime },
                SkillComponent.CODEC.listOf().optionalFieldOf("on_immune_to_damage", listOf()).forGetter { it.onImmuneToDamage },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::InvincibilityComponent)
        }
    }

}