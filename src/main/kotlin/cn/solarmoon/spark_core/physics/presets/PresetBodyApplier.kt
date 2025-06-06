package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithBoundingBoxTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.monster.Vindicator
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

object PresetBodyApplier {

    @SubscribeEvent
    private fun onEntityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level

//        if (entity is Player || entity is Zombie || entity is Vindicator) {
//            entity.model.bones.values.filterNot { it.name in listOf("rightItem", "leftItem") }.forEach {
//                val body = PhysicsRigidBody(it.name, entity, CompoundCollisionShape())
//
//                entity.bindBody(body, event.level.physicsLevel, true) {
//                    (body.collisionShape as CompoundCollisionShape).initWithAnimatedBone(it)
//                    body.isContactResponse = false
//                    body.setGravity(Vector3f.ZERO)
//                    body.setEnableSleep(false)
//                    body.isKinematic = true
//                    body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_OBJECT or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
//                    body.addPhysicsTicker(MoveWithAnimatedBoneTicker(it.name))
//                }
//            }
//        } else {
//            entity.apply {
//                val size = Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).div(2.0).toBVector3f()
//                val body = PhysicsRigidBody("body", entity, BoxCollisionShape(size))
//                bindBody(body, event.level.physicsLevel) {
//                    body.isContactResponse = false
//                    body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_OBJECT or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
//                    body.setGravity(Vector3f.ZERO)
//                    body.setEnableSleep(false)
//                    body.isKinematic = true
//                    body.addPhysicsTicker(MoveWithBoundingBoxTicker(true))
//                }
//            }
//        }
        entity.apply {
            val size = Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).div(2.0).toBVector3f()
            val body = PhysicsRigidBody("body", entity, BoxCollisionShape(size))
            bindBody(body, event.level.physicsLevel) {
                body.isContactResponse = false
                body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_OBJECT or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
                body.setGravity(Vector3f.ZERO)
                body.setEnableSleep(false)
                body.isKinematic = true
                body.addPhysicsTicker(MoveWithBoundingBoxTicker(true))
            }
        }
    }

}