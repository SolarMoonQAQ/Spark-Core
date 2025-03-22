package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.getOwner
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVec3
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

class MoveWithBoundingBoxTicker(private val shapeOverride: Boolean = false) : PhysicsCollisionObjectTicker {

    override fun physicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {

    }

    override fun mcTick(body: PhysicsCollisionObject, level: Level) {
        if (body is PhysicsBody) {
            val entity = body.getOwner<Entity>() ?: return
            level.physicsLevel.submitImmediateTask {
                val bb = entity.boundingBox
                val targetPos = bb.center.toBVector3f()
                body.setPhysicsLocation(targetPos)
                if (shapeOverride) body.collisionShape = BoxCollisionShape(Vec3(bb.xsize, bb.ysize, bb.zsize).div(2.0).toBVector3f())
            }
        }
    }

}