package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.util.PPhase
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

object PhysicsLevelApplier {

    @SubscribeEvent
    private fun load(event: LevelEvent.Load) {
        val level = event.level
        if (level is Level) {
            level.physicsLevel.start()
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
    private fun mainUpdate(event: LevelTickEvent.Pre) {
        event.level.physicsLevel.requestStep()
    }

    @SubscribeEvent
    private fun mcLevelTask(event: LevelTickEvent.Pre) {
        val level = event.level
        level.processTasks(PPhase.ALL)
        level.processTasks(PPhase.PRE)
    }

    @SubscribeEvent
    private fun mcLevelTask(event: LevelTickEvent.Post) {
        val level = event.level
        level.processTasks(PPhase.ALL)
        level.processTasks(PPhase.POST)
    }

    /**
     * 区块加载时存入physicsLevel缓存，方便读取包含的方块
     */
    @SubscribeEvent
    private fun chunkLoad(event: ChunkEvent.Load) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        physLevel.terrainManager.onChunkLoaded(event.chunk.pos, event.chunk as LevelChunk)
    }

    /**
     * 区块卸载时从physicsLevel缓存中移除
     */
    @SubscribeEvent
    private fun chunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        physLevel.terrainManager.onChunkUnloaded(event.chunk.pos)
    }

}