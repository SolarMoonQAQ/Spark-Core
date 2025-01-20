package cn.solarmoon.spark_core.phys.thread

import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

object PhysThreadApplier {

    @SubscribeEvent
    private fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as Level
        level.getAllPhysLevel().values.filter { !it.customApply }.forEach {
            it.load()
        }
    }

    @SubscribeEvent
    private fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as Level
        level.getAllPhysLevel().values.filter { !it.customApply }.forEach {
            it.unLoad()
        }
    }

    @SubscribeEvent
    private fun levelTicker(event: LevelTickEvent.Pre) {
        val level = event.level

        val laterConsumers = (level as ILaterConsumerHolder).consumers
        while (laterConsumers.isNotEmpty()) { laterConsumers.removeLastOrNull()?.invoke() }

        level.getAllPhysLevel().values.forEach {
            it.world.bodyIteration.forEach {
                it.tick()
            }
        }

    }

}