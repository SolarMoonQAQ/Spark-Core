package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.event.PhysLevelTickEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.neoforge.common.NeoForge

class ClientPhysLevel(
    id: String,
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
        NeoForge.EVENT_BUS.post(PhysLevelTickEvent(this))
    }

}