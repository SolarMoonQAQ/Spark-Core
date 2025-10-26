package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.util.onEvent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
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
            val x = (bb.xsize / 2).toFloat()
            val y = (bb.ysize / 3).toFloat()
            val z = (bb.zsize / 2).toFloat()
            val body = PhysicsRigidBody(BoxCollisionShape(x, y, z)).apply {
                name = "body"
                owner = entity
                isKinematic = true
                isContactResponse = false
                collisionGroup = CollisionGroups.PAWN
//                addCollideWithGroup(CollisionGroups.TERRAIN)
                onEvent<PhysicsBodyEvent.Tick> {
                    val bb = entity.boundingBox
                    val x: Float = (bb.xsize / 2).toFloat()
                    val y: Float = (bb.ysize / 2).toFloat()
                    val z: Float = (bb.zsize / 2).toFloat()
                    val shape = collisionShape as BoxCollisionShape
                    val halfExtent = shape.getHalfExtents(null)
                    //仅尺寸发生变化时更新碰撞体形状
                    if (halfExtent.x != x || halfExtent.y != y || halfExtent.z != z) {
                        entity.physicsLevel.submitImmediateTask {
                            collisionShape = BoxCollisionShape(x, y, z)
                        }
                    }
                }
            }
            body.attachToEntity(entity)
            event.level.addPhysicsBody(body)
        }
    }

    @SubscribeEvent
    private fun removeBodyForEntity(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.allPhysicsBodies.forEach {
            event.level.removePhysicsBody(it.value)
        }
    }

}