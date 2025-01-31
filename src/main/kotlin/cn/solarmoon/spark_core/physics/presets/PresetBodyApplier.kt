package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsContactEvent
import cn.solarmoon.spark_core.physics.getOwner
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.player.Player
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
            val size = Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).div(1.0).toBVector3f()
            val body = PhysicsRigidBody("body", this, BoxCollisionShape(size))
            bindBody(body, level.physicsLevel) {
                setGravity(Vector3f.ZERO)
                addPhysicsTicker(MoveWithBoundingBoxTicker())
            }
        }
    }

    @SubscribeEvent
    private fun on(event: PhysicsContactEvent.Process) {
        if (event.o1.getOwner<Player>() != null) SparkCore.LOGGER.info("¹þ¹þ¹þ")
    }

}