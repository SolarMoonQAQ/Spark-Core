package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
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
import net.neoforged.neoforge.event.tick.LevelTickEvent.Pre

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
    private fun chunkTicketUpdate(event: ChunkTicketLevelUpdatedEvent){
        val level = event.level as Level
        val physLevel: PhysicsLevel = level.physicsLevel
        val chunkPos = ChunkPos(event.chunkPos)
        // 卸载优先级过低的物理区块，而非等到区块卸载事件触发
        if (event.newTicketLevel > 33 && physLevel.tickCount > 0 && physLevel.terrainManager.loaded(chunkPos)){
            physLevel.terrainManager.onChunkUnloaded(chunkPos)
        }
    }
    //__________以下内容转用mixin实现，服务端世界发生方块更新时标记脏section__________
//    /**
//     * 发生方块变动时触发地形刚体管理器相关方法
//     */
//    @SubscribeEvent
//    private fun chunkBlockBreak(event: BlockEvent.BreakEvent) {
//        if (!event.isCanceled) {
//            val level = event.level as Level
//            val physLevel: PhysicsLevel = level.physicsLevel
//            val blockPos = event.pos
//            onBlockUpdated(setOf(blockPos), physLevel)
//        }
//    }
//
//    /**
//     * 发生方块变动时触发地形刚体管理器相关方法
//     */
//    @SubscribeEvent
//    private fun chunkBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
//        if (!event.isCanceled) {
//            val level = event.level as Level
//            val physLevel: PhysicsLevel = level.physicsLevel
//            if (event is BlockEvent.EntityMultiPlaceEvent) {
//                val blocks = mutableSetOf<BlockPos>()
//                for (blockSnapshot in event.replacedBlockSnapshots) {
//                    blocks.add(blockSnapshot.pos)
//                }
//                onBlockUpdated(blocks, physLevel)
//            } else {
//                val blockPos = event.pos
//                onBlockUpdated(setOf(blockPos), physLevel)
//            }
//        }
//    }
//
//    /**
//     * 活塞推动方块时触发地形刚体管理器相关方法
//     */
//    @SubscribeEvent
//    private fun onPistonMove(event: PistonEvent.Post) {
//        val level = event.level as Level
//        val physLevel: PhysicsLevel = level.physicsLevel
//        val blocks = mutableSetOf<BlockPos>()
//        var blockPos = event.faceOffsetPos
//        repeat(16) { // 考虑所有可能被活塞影响到的方块
//            blocks.add(blockPos)
//            blockPos = blockPos.relative(event.direction)
//        }
//        onBlockUpdated(blocks, physLevel)
//    }
//
//    @JvmStatic
//    private fun onBlockUpdated(blocks: Set<BlockPos>, physLevel: PhysicsLevel) {
//        // 将发生变化的区块section标记为脏，步进时统一更新
//        physLevel.terrainManager.onBlockUpdated(blocks)
//    }

}