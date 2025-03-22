package cn.solarmoon.spark_core.physics.event

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional
import cn.solarmoon.spark_core.physics.terrain.TerrainChunkPos3D

/**
 * 物理事件数据包,用于服务端和客户端之间同步物理事件
 * 当服务端触发物理相关事件时(方块破坏、放置、玩家移动),将创建此数据包发送到客户端
 * 客户端接收后会相应地处理物理事件
 */
class PhysicsEventPayload private constructor(
    val eventType: EventType,
    val blockPos: BlockPos?,    // 对于方块事件
    val chunkPos: ChunkPos?,    // 对于区块相关事件
    val blockState: BlockState? // 对于方块状态变更事件
) : CustomPacketPayload {

    /**
     * 方块事件构造方法
     */
    constructor(eventType: EventType, blockPos: BlockPos, blockState: BlockState? = null) : 
        this(eventType, blockPos, null, blockState)

    /**
     * 区块事件构造方法
     */
    constructor(eventType: EventType, chunkPos: ChunkPos) : 
        this(eventType, null, chunkPos, null)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    /**
     * 物理事件类型枚举
     */
    enum class EventType {
        BLOCK_BREAK,     // 方块破坏
        BLOCK_PLACE,     // 方块放置
        BLOCK_CHANGE,    // 方块状态变更
        PLAYER_MOVE,     // 玩家移动
        CHUNK_LOAD,      // 区块加载
        CHUNK_UNLOAD;    // 区块卸载

        companion object {
            val STREAM_CODEC = ByteBufCodecs.INT.map({ EventType.values()[it] }, { it.ordinal })
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<PhysicsEventPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "physics_event")
        )

        /**
         * 流编解码器,用于序列化和反序列化数据包
         */
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PhysicsEventPayload> = StreamCodec.composite(
            EventType.STREAM_CODEC, PhysicsEventPayload::eventType,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), { it.blockPos?.let { pos -> Optional.of(pos) } ?: Optional.empty() },
            NeoForgeStreamCodecs.CHUNK_POS.apply(ByteBufCodecs::optional), { it.chunkPos?.let { pos -> Optional.of(pos) } ?: Optional.empty() },
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).apply(ByteBufCodecs::optional), { it.blockState?.let { state -> Optional.of(state) } ?: Optional.empty() },
            { eventType, optBlockPos, optChunkPos, optBlockState -> 
                PhysicsEventPayload(
                    eventType, 
                    if (optBlockPos.isPresent) optBlockPos.get() else null,
                    if (optChunkPos.isPresent) optChunkPos.get() else null,
                    if (optBlockState.isPresent) optBlockState.get() else null
                )
            }
        )

        /**
         * 在客户端处理接收到的物理事件
         */
        fun handleInClient(payload: PhysicsEventPayload, context: IPayloadContext) {
            val level = context.player().level()
            
            // 确保只在客户端处理
            if (!level.isClientSide) return
            
            try {
                when (payload.eventType) {
                    EventType.BLOCK_BREAK, EventType.BLOCK_PLACE, EventType.BLOCK_CHANGE -> {
                        if (payload.blockPos == null) {
                            SparkCore.LOGGER.error("收到方块事件但没有方块位置: ${payload.eventType}")
                            return
                        }
                        
                        // 处理方块相关事件
                        level.physicsLevel.terrainManager.onBlockChanged(
                            payload.blockPos, 
                            payload.blockState ?: level.getBlockState(payload.blockPos)
                        )
                        SparkCore.LOGGER.debug("客户端处理${payload.eventType}事件, 位置: ${payload.blockPos}")
                    }
                    
                    EventType.PLAYER_MOVE -> {
                        // 玩家移动事件通常需要更新周围区块
                        // 这里我们调用mergePlayerChunk来处理玩家周围的区块
                        level.physicsLevel.terrainManager.mergePlayerChunk(context.player())
                        SparkCore.LOGGER.debug("客户端处理玩家移动事件")
                    }
                    
                    EventType.CHUNK_LOAD -> {
                        if (payload.chunkPos == null) {
                            SparkCore.LOGGER.error("收到区块加载事件但没有区块位置")
                            return
                        }
                        
                        // 客户端区块加载事件处理
                        // 通常在区块加载时需要更新物理世界
//                        SparkCore.LOGGER.debug("客户端处理区块加载事件, 区块: ${payload.chunkPos}")
                        // 目前在客户端无需特殊处理,因为区块加载时会自动处理物理更新
                    }
                    
                    EventType.CHUNK_UNLOAD -> {
                        if (payload.chunkPos == null) {
                            SparkCore.LOGGER.error("收到区块卸载事件但没有区块位置")
                            return
                        }
                        
                        // 客户端区块卸载事件处理
                        // 通常在区块卸载时需要清理物理世界中的相关对象
                        SparkCore.LOGGER.debug("客户端处理区块卸载事件, 区块: ${payload.chunkPos}")
                        // 遍历所有可能的Y轴索引,释放所有相关的三维区块
                        for (y in 0 until TerrainChunkPos3D.VERTICAL_CHUNKS_COUNT) {
                            val chunk3D = TerrainChunkPos3D(payload.chunkPos.x, y, payload.chunkPos.z)
                            level.physicsLevel.terrainManager.chunkCache.releaseChunk(chunk3D)
                        }
                    }
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("处理物理事件时发生错误", e)
            }
        }
    }
} 