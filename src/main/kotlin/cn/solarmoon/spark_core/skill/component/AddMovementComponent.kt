package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.addRelativeMovement
import com.mojang.serialization.Codec
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

class AddMovementComponent(
    val add: Vec3 = Vec3.ZERO
) {

    fun add(entity: Entity, relative: Vec3) {
        entity.addRelativeMovement(relative, add)
    }

    companion object {
        val CODEC: Codec<AddMovementComponent> = Vec3.CODEC.xmap({ AddMovementComponent(it) }, { it.add })
    }

}