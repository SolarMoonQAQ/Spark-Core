package cn.solarmoon.spark_core.physics.level

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySelector

class ClientPhysicsLevel(
    override val mcLevel: ClientLevel
) : PhysicsLevel("Client PhysicsThread", mcLevel) {

    override fun requestEntities(): List<Entity> {
        val mc = Minecraft.getInstance()
        val renderDistance = mc.gameRenderer.renderDistance
        mc.cameraEntity?.let { player ->
            val level = mc.level ?: return@let
            // 修复：旁观者模式下动画不进行，实际上是只有本地玩家没有进行动画
            return level.getEntities(null, player.boundingBox.inflate(renderDistance.toDouble()), EntitySelector.ENTITY_STILL_ALIVE)
        }
        return emptyList()
    }

    val partialTicks: Float
        get() {
            val currentTime = System.nanoTime()
            val elapsedSinceLastTick = (currentTime - lastPhysicsTickTime) / 1e9f
            return (elapsedSinceLastTick * 20).coerceIn(0f, 1f)
        }

}