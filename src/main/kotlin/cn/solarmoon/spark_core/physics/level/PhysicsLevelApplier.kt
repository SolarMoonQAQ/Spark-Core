package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsTickEvent
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.level.LevelEvent
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

object PhysicsLevelApplier {

    @SubscribeEvent
    private fun load(event: LevelEvent.Load) {
        val level = event.level
        if (level is Level) {
            level.physicsLevel.load()
        }
    }

    @SubscribeEvent
    private fun unLoad(event: LevelEvent.Unload) {
        val level = event.level
        if (level is Level) {
            level.physicsLevel.close()
        }
    }

}