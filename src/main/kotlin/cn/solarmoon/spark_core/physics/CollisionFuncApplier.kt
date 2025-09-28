package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.body.addPhysicsBody
import cn.solarmoon.spark_core.physics.body.attachToEntity
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

object CollisionFuncApplier {

    @SubscribeEvent
    private fun test(event: EntityJoinLevelEvent) {
        if (event.entity is Player) {
            val player = event.entity

//            val body = PhysicsRigidBody(BoxCollisionShape(2F)).apply {
//                setGravity(Vector3f())
//                isKinematic = true
//                attachToEntity(player)
//            }
//
//            event.level.addPhysicsBody(body)

        }
    }

}