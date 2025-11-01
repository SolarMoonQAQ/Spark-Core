package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsLevelInitEvent
import cn.solarmoon.spark_core.util.PPhase
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

object PhysicsLevelApplier {

    @SubscribeEvent
    private fun load(event: LevelEvent.Load) {
        val level = event.level
        if (level is Level) {
            level.physicsLevel.start {
                // 广播通知物理世界初始化完成
                NeoForge.EVENT_BUS.post(PhysicsLevelInitEvent(level.physicsLevel))
            }
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
    private fun mcLevelTask(event: LevelTickEvent.Pre) {
        val level = event.level
        level.processTasks(PPhase.ALL)
        level.processTasks(PPhase.PRE)
        event.level.physicsLevel.requestStep()
    }

    @SubscribeEvent
    private fun mcLevelTask(event: LevelTickEvent.Post) {
        val level = event.level
        level.processTasks(PPhase.ALL)
        level.processTasks(PPhase.POST)
    }

    /**
     * 区块加载时触发地形刚体管理器相关方法
     */
    @SubscribeEvent
    private fun chunkLoad(event: ChunkEvent.Load) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        physLevel.terrainManager.onChunkLoaded(event.chunk.pos, event.chunk as LevelChunk)
    }

    /**
     * 区块卸载时触发地形刚体管理器相关方法
     */
    @SubscribeEvent
    private fun chunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        physLevel.terrainManager.onChunkUnloaded(event.chunk.pos)
    }

    @SubscribeEvent
    private fun chunkTicketUpdate(event: ChunkTicketLevelUpdatedEvent) {
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        val chunkPos = ChunkPos(event.chunkPos)
        // 卸载优先级过低的物理区块，而非等到区块卸载事件触发
        if (event.newTicketLevel > 33 && physLevel.tickCount > 0 && physLevel.terrainManager.loaded(chunkPos)) {
            physLevel.terrainManager.unloadPhysicsChunk(chunkPos)
        }
    }

}