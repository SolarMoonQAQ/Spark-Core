package cn.solarmoon.spark_core.physics.host

import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

object PhysicsHostApplier {

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.removeAllBodies()
    }

    @SubscribeEvent
    private fun onEntityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        entity.apply {
            val size = Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).div(1.0).toBVector3f()
            val body = PhysicsRigidBody(BoxCollisionShape(size)).apply {
                isNoGravity = true
            }
            bindBody("body", body, level.physicsLevel)
        }
    }

}