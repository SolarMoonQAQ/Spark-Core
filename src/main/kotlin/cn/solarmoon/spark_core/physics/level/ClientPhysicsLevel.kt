package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge

class ClientPhysicsLevel(
    override val mcLevel: ClientLevel
): PhysicsLevel("Client PhysicsThread", mcLevel) {

    override fun prePhysicsTick(space: PhysicsSpace, timeStep: Float) {
        super.prePhysicsTick(space, timeStep)

        val renderDistance = Minecraft.getInstance().gameRenderer.renderDistance

        Minecraft.getInstance().also { mc ->
            mc.player?.let {
                val level = mc.level ?: return@let
                level.getEntities(null, it.boundingBox.inflate(renderDistance.toDouble()))
                    .forEach {
                        NeoForge.EVENT_BUS.post(PhysicsEntityTickEvent(it))
                    }
            }
        }
    }

    val partialTicks: Float get() = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(true)

}