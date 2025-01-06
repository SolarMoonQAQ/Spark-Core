package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysLevelRegisterEvent
import cn.solarmoon.spark_core.event.PhysLevelTickEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
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
        level.getAllPhysLevel().values.forEach {
            it.physWorld.world.bodyIteration.forEach {
                it.tick()
            }
        }

        level.getPhysLevel().physTick()
    }

}