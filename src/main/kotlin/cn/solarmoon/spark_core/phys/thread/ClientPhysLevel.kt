package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.PhysTickEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

class ClientPhysLevel(
    id: ResourceLocation,
    name: String,
    override val level: ClientLevel,
    tickStep: Long,
    customApply: Boolean
): PhysLevel(id, name, level, tickStep, customApply) {

    val partialTicks: Float get() {
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastTickTime) / 1_000_000.0
        return (deltaTime / tickStep).toFloat().coerceIn(0f, 1f)
    }

    override fun physTick() {
        super.physTick()
        NeoForge.EVENT_BUS.post(PhysTickEvent.Level(this))

        val player = Minecraft.getInstance().player ?: return
        level.getEntities(null, player.boundingBox.inflate(1024.0)).forEach { NeoForge.EVENT_BUS.post(PhysTickEvent.Entity(it, this)) }
    }

}