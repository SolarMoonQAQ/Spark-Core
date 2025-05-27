package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge

class ClientPhysicsLevel(
    override val mcLevel: ClientLevel
) : PhysicsLevel("Client PhysicsThread", mcLevel) {

    override fun prePhysicsTick(space: PhysicsSpace, timeStep: Float) {
        super.prePhysicsTick(space, timeStep)

        Minecraft.getInstance().also { mc ->
            mc.player?.let { player ->
                val level = mc.level ?: return@let
                val entities = level.entities.all

                entities.filterNotNull().forEach {
                    if (!it.isRemoved && it.isAlive)
                        NeoForge.EVENT_BUS.post(PhysicsEntityTickEvent(it))
                }
            }
        }
    }

    val partialTicks: Float
        get() {
            val currentTime = System.nanoTime()
            val elapsedSinceLastTick = (currentTime - lastPhysicsTickTime) / 1e9f
            return (elapsedSinceLastTick * 20).coerceIn(0f, 1f)
        }

}