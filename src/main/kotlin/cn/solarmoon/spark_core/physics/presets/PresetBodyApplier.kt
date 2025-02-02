package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

object PresetBodyApplier {

    @SubscribeEvent
    private fun onEntityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        entity.apply {
            val size = Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).div(2.0).toBVector3f()
            val body = PhysicsRigidBody("body", entity, BoxCollisionShape(size))
            bindBody(body, level.physicsLevel) {
                isContactResponse = false
                setGravity(Vector3f.ZERO)
                addPhysicsTicker(MoveWithBoundingBoxTicker())
            }

            if (entity is IEntityAnimatable<*>) {
                val test = PhysicsRigidBody("test", entity, CompoundCollisionShape())
                bindBody(test, level.physicsLevel) {
                    isContactResponse = false
                    setGravity(Vector3f.ZERO)
                    entity.model.bones.values.filterNot { it.name in listOf("rightItem", "leftItem") }.forEach {
                        addPhysicsTicker(MoveWithAnimatedBoneTicker(it.name))
                        (collisionShape as CompoundCollisionShape).initWithAnimatedBone(it)
                    }
                }
            }
        }
    }

}