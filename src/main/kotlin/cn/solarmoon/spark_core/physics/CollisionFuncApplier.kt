package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.component.CollisionObjectEvent
import cn.solarmoon.spark_core.physics.component.Authority
import cn.solarmoon.spark_core.physics.component.RigidBodyComponent
import cn.solarmoon.spark_core.util.onEvent
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import org.joml.Vector3f

object CollisionFuncApplier {

    @SubscribeEvent
    private fun test(event: EntityJoinLevelEvent) {
        if (event.entity is Player) {
            val player = event.entity

            val body = RigidBodyComponent("TEST", Authority.SERVER, BoxCollisionShape(1.0f), event.level).apply {
                isKinematic = true
                gravity = Vector3f()
                bindHost(player)
                attachToEntity(player)
                addToLevel()
            }

        }
    }

}