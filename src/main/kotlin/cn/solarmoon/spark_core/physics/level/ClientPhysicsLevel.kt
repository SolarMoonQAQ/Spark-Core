package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsTickEvent
import com.github.stephengold.sport.physics.AabbGeometry
import com.jme3.bullet.PhysicsSpace
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.neoforge.common.NeoForge

class ClientPhysicsLevel(
    override val mcLevel: ClientLevel
): PhysicsLevel("Client PhysicsThread") {

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        super.physicsTick(space, timeStep)

        val renderDistance = Minecraft.getInstance().gameRenderer.renderDistance

        Minecraft.getInstance().also { mc ->
            mc.player?.let {
                val level = mc.level ?: return@let
                level.getEntities(null, it.boundingBox.inflate(renderDistance.toDouble()))
                    .forEach {
                        NeoForge.EVENT_BUS.post(PhysicsTickEvent.Entity(it, this))
                    }
            }
        }
    }

}