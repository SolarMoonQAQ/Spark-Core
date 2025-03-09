package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.addRelativeMovement
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent

class AddTargetMovementComponent(
    val add: Vec3 = Vec3.ZERO
): SkillComponent() {

    override fun onAttach(): Boolean {
        val holder = skill.holder as? Entity ?: return false
        val relative = holder.position()
        skill.getTargets().forEach {
            it.addRelativeMovement(relative, add)
        }
        return false
    }

    override fun onTargetKnockBack(event: LivingKnockBackEvent) {
        event.isCanceled = true
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<AddTargetMovementComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Vec3.CODEC.optionalFieldOf("value", Vec3.ZERO).forGetter { it.add }
            ).apply(it, ::AddTargetMovementComponent)
        }
    }

}