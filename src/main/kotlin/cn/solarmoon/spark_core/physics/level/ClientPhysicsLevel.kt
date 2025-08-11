package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.EntitySelector
import net.neoforged.neoforge.common.NeoForge

class ClientPhysicsLevel(
    override val mcLevel: ClientLevel
) : PhysicsLevel("Client PhysicsThread", mcLevel) {

    override fun prePhysicsTick(space: PhysicsSpace, timeStep: Float) {
        super.prePhysicsTick(space, timeStep)

        val renderDistance = Minecraft.getInstance().gameRenderer.renderDistance
        Minecraft.getInstance().also { mc ->
            mc.cameraEntity?.let { player ->
                val level = mc.level ?: return@let
                // 修复：旁观者模式下动画不进行，实际上是只有本地玩家没有进行动画
                level.getEntities(null, player.boundingBox.inflate(renderDistance.toDouble()), EntitySelector.ENTITY_STILL_ALIVE)
                    .filterNotNull().forEach {
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