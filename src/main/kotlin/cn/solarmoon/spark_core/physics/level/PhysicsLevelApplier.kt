package cn.solarmoon.spark_core.physics.level

import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

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

    @SubscribeEvent
    private fun mcLevelTask(event: LevelTickEvent.Post) {
        val level = event.level
        level.processTasks()
    }

}