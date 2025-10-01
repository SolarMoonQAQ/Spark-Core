package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.physics.PhysicsHost
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.decoration.BlockAttachedEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object CollisionFuncApplier {

    @SubscribeEvent
    private fun addBodyForEntity(event: EntityJoinLevelEvent) {
        val entity = event.entity
        if (entity is PhysicsHost && entity !is BlockAttachedEntity && entity !is CollisionObjectEntity) {
            val bb = entity.boundingBox
            val x: Float = (bb.xsize / 1.2).toFloat()
            val y: Float = (bb.ysize / 1.2).toFloat()
            val z: Float = (bb.zsize / 1.2).toFloat()
            val body = entity.createPhysicsBody(BoxCollisionShape(x, y, z), 60f, "entity_bounding_box").apply {
                isKinematic = true
                setProtectGravity(true)
                isContactResponse = false
                setGravity(Vector3f.ZERO)
                collisionGroup = CollisionGroups.PHYSICS_BODY
                collideWithGroups = CollisionGroups.PHYSICS_BODY
                addCollideWithGroup(CollisionGroups.TERRAIN)
            }
            body.attachToEntity(entity)
            entity.addPhysicsBody(body)
        }
    }

    @SubscribeEvent
    private fun updateBodyForEntity(event: EntityTickEvent.Post) {
        val entity = event.entity
        if (entity is PhysicsHost && entity !is BlockAttachedEntity && entity !is CollisionObjectEntity) {
            val body = entity.getPhysicsBody("entity_bounding_box")
            if (body != null) {
                val bb = entity.boundingBox
                val x: Float = (bb.xsize / 1.2).toFloat()
                val y: Float = (bb.ysize / 1.2).toFloat()
                val z: Float = (bb.zsize / 1.2).toFloat()
                entity.physicsLevel.submitImmediateTask {
                    body.collisionShape = BoxCollisionShape(x, y, z)
                }
            }
        }
    }

    @SubscribeEvent
    private fun removeBodyForEntity(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        if (entity is PhysicsHost && entity !is BlockAttachedEntity && entity !is CollisionObjectEntity) {
            val body = entity.getPhysicsBody("entity_bounding_box")
            if (body != null) {
                entity.removePhysicsBody(body)
            }
        }
    }

}