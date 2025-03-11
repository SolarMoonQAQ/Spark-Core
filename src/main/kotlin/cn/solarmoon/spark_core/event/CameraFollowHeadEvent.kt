package cn.solarmoon.spark_core.event

import net.minecraft.client.Camera
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.event.entity.EntityEvent

class CameraFollowHeadEvent(
    entity: Entity,
    val camera: Camera,
    val originEnabled: Boolean
): EntityEvent(entity) {

    var isEnabled: Boolean = originEnabled

}