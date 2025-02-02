package cn.solarmoon.spark_core.physics.collision

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

object CollisionFunctionApplier {

    @SubscribeEvent
    private fun mcTick(event: LevelTickEvent.Post) {
        val level = event.level
        level.physicsLevel.world.pcoList.forEach { body ->
            body.tickers.forEach {
                it.mcTick(body, level)
            }
        }
    }

}