package cn.solarmoon.spark_core.physics.level

import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.ChunkEvent
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

    /**
     * 区块加载时存入physicsLevel缓存，方便读取包含的方块
     */
    @SubscribeEvent
    private fun chunkLoad(event: ChunkEvent.Load) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        physLevel.terrainChunks[event.chunk.pos] = event.chunk
        //TODO:整合全区块方块为单一碰撞体积，减少资源占用
    }

    /**
     * 区块卸载时从physicsLevel缓存中移除
     */
    @SubscribeEvent
    private fun chunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        if (physLevel.terrainChunks.containsKey(event.chunk.pos)){
            physLevel.terrainChunks.remove(event.chunk.pos)
        }
    }

}