package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.getOwner
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class MoveWithBoundingBoxTicker(private val shapeOverride: Boolean = false) : PhysicsCollisionObjectTicker {

    override fun postPhysicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {

    }

    override fun mcTick(body: PhysicsCollisionObject, level: Level) {
        if (body is PhysicsBody) {
            val entity = body.getOwner<Entity>() ?: return
            entity.level().physicsLevel.submitImmediateTask(PPhase.PRE) {
                val bb = entity.boundingBox
                val targetPos = bb.center.toBVector3f()
                body.setPhysicsLocation(targetPos)
                if (shapeOverride) body.collisionShape = BoxCollisionShape(Vec3(bb.xsize, bb.ysize, bb.zsize).div(2.0).toBVector3f())
            }
        }
    }

}