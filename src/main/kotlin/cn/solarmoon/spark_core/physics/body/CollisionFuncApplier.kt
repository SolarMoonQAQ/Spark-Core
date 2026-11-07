package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.api.physicsLevel
import cn.solarmoon.spark_core.physics.toVec3d
import cn.solarmoon.spark_core.util.Subscription
import cn.solarmoon.spark_core.util.onEvent
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent

object CollisionFuncApplier {

    private class DefaultBody(val level: Level, shape: BoxCollisionShape) : PhysicsRigidBody(shape) {
        lateinit var follow: Subscription
    }

    @SubscribeEvent
    private fun addBodyForEntity(event: EntityJoinLevelEvent) {
        refreshDefaultBody(event.entity, event.level)
    }

    @JvmStatic
    fun refreshDefaultBody(entity: Entity, level: Level) {
        if (entity is CollisionObjectEntity) return
        val physics = level.physicsLevel
        physics.submitImmediateTask {
            synchronized(entity) {
                if (entity.isRemoved || entity.level() !== level) return@synchronized
                val current = entity.getPhysicsBody("body")
                val enabled = entity.shouldCreateDefaultPhysicsBody()
                if (current is DefaultBody && (current.level !== level || !enabled)) {
                    removeDefaultBody(current)
                }
                if (enabled && entity.getPhysicsBody("body") == null) {
                    createDefaultBody(entity, level)
                }
            }
        }
    }

    private fun createDefaultBody(entity: Entity, level: Level) {
        val bb = entity.boundingBox
        val body = DefaultBody(level, BoxCollisionShape(
            (bb.xsize / 2).toFloat(), (bb.ysize / 2).toFloat(), (bb.zsize / 2).toFloat()
        )).apply {
            name = "body"
            isKinematic = true
            isContactResponse = false
            collisionGroup = CollisionGroups.PAWN
            setCollideWithGroups(CollisionGroups.NONE)
            setPhysicsLocationDp(bb.center.toVec3d())
            follow = onEvent<PhysicsBodyEvent.Tick> {
                val bounds = entity.boundingBox
                setPhysicsLocationDp(bounds.center.toVec3d())
                val x = (bounds.xsize / 2).toFloat()
                val y = (bounds.ysize / 2).toFloat()
                val z = (bounds.zsize / 2).toFloat()
                val halfExtent = (collisionShape as BoxCollisionShape).getHalfExtents(null)
                if (halfExtent.x != x || halfExtent.y != y || halfExtent.z != z) {
                    level.physicsLevel.submitImmediateTask {
                        if (isInWorld) collisionShape = BoxCollisionShape(x, y, z)
                    }
                }
            }
        }
        body.owner = entity
        level.physicsLevel.world.addCollisionObject(body)
    }

    private fun removeDefaultBody(body: DefaultBody) {
        body.owner = null
        val physics = body.level.physicsLevel
        physics.submitImmediateTask {
            body.follow.dispose()
            if (body.isInWorld) physics.world.removeCollisionObject(body)
        }
    }

    @SubscribeEvent
    private fun removeBodyForEntity(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        synchronized(entity) {
            entity.allPhysicsBodies.values.toList().forEach { body ->
                if (body is DefaultBody) {
                    if (body.level === event.level) removeDefaultBody(body)
                } else {
                    event.level.removePhysicsBody(body)
                }
            }
        }
    }

}
