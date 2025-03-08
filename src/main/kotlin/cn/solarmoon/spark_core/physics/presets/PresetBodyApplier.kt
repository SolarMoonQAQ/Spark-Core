package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithBoundingBoxTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

object PresetBodyApplier {

    @SubscribeEvent
    private fun onEntityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity

        if (entity is Player || entity is Zombie) {
            entity.model.bones.values.filterNot { it.name in listOf("rightItem", "leftItem") }.forEach {
                val b = PhysicsRigidBody(it.name, entity, CompoundCollisionShape())

                entity.bindBody(b, event.level.physicsLevel, true) {
                    (collisionShape as CompoundCollisionShape).initWithAnimatedBone(it)
                    isContactResponse = false
                    setGravity(Vector3f.ZERO)
                    collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_01 or PhysicsCollisionObject.COLLISION_GROUP_02
                    addPhysicsTicker(MoveWithAnimatedBoneTicker(it.name))
                }
            }
        } else {
            entity.apply {
                val size = Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).div(2.0).toBVector3f()
                val body = PhysicsRigidBody("body", entity, BoxCollisionShape(size))
                bindBody(body, event.level.physicsLevel) {
                    isContactResponse = false
                    collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_01 or PhysicsCollisionObject.COLLISION_GROUP_02
                    setGravity(Vector3f.ZERO)
                    addPhysicsTicker(MoveWithBoundingBoxTicker(true))
                }
            }
        }
    }

}