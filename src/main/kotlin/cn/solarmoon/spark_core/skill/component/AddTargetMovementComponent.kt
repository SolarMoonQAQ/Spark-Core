package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.addRelativeMovement
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.registry.common.SparkSkillContext
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

class AddTargetMovementComponent(
    val add: Vec3 = Vec3.ZERO
): SkillComponent() {

    override fun onAttach() {
        val holder = skill.holder as? Entity ?: return
        val relative = holder.position()
        val target = skill.blackBoard.require(SparkSkillContext.ENTITY_TARGET, this)
        target.addRelativeMovement(relative, add)
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