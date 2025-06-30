package cn.solarmoon.spark_core.physics.entity

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * 实体物理应用器，处理实体物理冻结等状态
 */
object EntityPhysicsApplier {

    // 冻结距离配置（可通过配置文件调整）
    private const val FREEZE_DISTANCE = 64.0
    
    // 冻结检查间隔（每N个tick检查一次，避免频繁检查）
    private const val CHECK_INTERVAL = 20
    private var tickCounter = 0

    // 缓存已处理的实体ID，避免重复处理
    private val processedEntities = ConcurrentHashMap<Int, Boolean>()
    
    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        // 实体加入世界时初始化其物理状态
        if (event.level.isClientSide) return
        checkAndUpdateEntityFreezeState(event.entity)
    }
    
    @SubscribeEvent
    fun onLevelTick(event: LevelTickEvent.Post) {
        tickCounter++
        if (event.level.isClientSide) return
        if (tickCounter % CHECK_INTERVAL != 0) return
    
        val level = event.level as ServerLevel
        // 清理缓存
        processedEntities.clear()
        
        // 获取所有玩家位置，用于距离检查
        val playerPositions = level.players().map { it.blockPosition() }
    
        // 如果没有玩家，冻结所有实体
        if (playerPositions.isEmpty()) {
            freezeAllEntitiesInLevel(level)
            return
        }

        // 对每个玩家进行冻结逻辑
        playerPositions.forEach { playerPos ->
            // 创建搜索区域
            val searchArea = AABB(
                playerPos.x - FREEZE_DISTANCE, level.minBuildHeight.toDouble(), playerPos.z - FREEZE_DISTANCE,
                playerPos.x + FREEZE_DISTANCE, level.maxBuildHeight.toDouble(), playerPos.z + FREEZE_DISTANCE
            )

            // 获取区域内的所有实体
            level.getEntities(null, searchArea) { entity ->
                if (entity is PhysicsHost && !processedEntities.containsKey(entity.id)) {
                    processedEntities[entity.id] = true
                
                    // 计算到玩家的距离
                    val distanceSq = entity.distanceToSqr(playerPos.x.toDouble(), playerPos.y.toDouble(), playerPos.z.toDouble())
                
                    // 根据距离决定是否直接冻结
                    if (distanceSq > FREEZE_DISTANCE * FREEZE_DISTANCE * 0.7) {
                        if (!entity.isPhysicsFrozen()) {
                            entity.setPhysicsFrozen(true)
                            SparkCore.LOGGER.debug("距离过远，冻结实体物理: ${entity.javaClass.simpleName} (${entity.id})")
                        }
                    } else {
                        // 近处实体根据具体条件判断
                        checkAndUpdateEntityFreezeState(entity, playerPos)
                    }
                }
                false // 继续遍历
            }
        }
    }

    
    /**
     * 冻结Level中的所有实体
     */
    private fun freezeAllEntitiesInLevel(level: ServerLevel) {
        // 使用getEntities获取所有实体
        level.allEntities.forEach { entity ->
            if (entity is PhysicsHost && !entity.isPhysicsFrozen()) {
                entity.setPhysicsFrozen(true)
            }
        }
    }
    
    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val chunk = event.chunk as? LevelChunk ?: return
        if (event.level.isClientSide) return
        val level = chunk.level as ServerLevel
        val chunkPos = chunk.pos

        // 创建区块的AABB
        val chunkAABB = AABB(
            chunkPos.minBlockX.toDouble(), level.minBuildHeight.toDouble(), chunkPos.minBlockZ.toDouble(),
            (chunkPos.maxBlockX + 1).toDouble(), level.maxBuildHeight.toDouble(), (chunkPos.maxBlockZ + 1).toDouble()
        )
    
        // 获取区块内的实体
        level.getEntities(null, chunkAABB) { entity ->
            if (entity is PhysicsHost && !entity.isPhysicsFrozen()) {
                entity.setPhysicsFrozen(true)
                SparkCore.LOGGER.debug("区块卸载，冻结实体物理: ${entity.javaClass.simpleName} (${entity.id})")
            }
            false // 继续遍历
        }
    }
    
    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val chunk = event.chunk as? LevelChunk ?: return
        if (event.level.isClientSide) return
        val level = chunk.level as ServerLevel
        val chunkPos = chunk.pos
    
        // 创建区块的AABB
        val chunkAABB = AABB(
            chunkPos.minBlockX.toDouble(), level.minBuildHeight.toDouble(), chunkPos.minBlockZ.toDouble(),
            (chunkPos.maxBlockX + 1).toDouble(), level.maxBuildHeight.toDouble(), (chunkPos.maxBlockZ + 1).toDouble()
        )
    
        // 获取所有玩家位置
        val playerPositions = level.players().map { it.blockPosition() }
    
        // 获取区块内的实体
        level.getEntities(null, chunkAABB) { entity ->
            if (entity is PhysicsHost) {
                if (playerPositions.isNotEmpty()) {
                    playerPositions.forEach { playerPos ->
                        checkAndUpdateEntityFreezeState(entity, playerPos)
                    }
                } else {
                    // 如果没有玩家，保持冻结状态
                    if (!entity.isPhysicsFrozen()) {
                        entity.setPhysicsFrozen(true)
                    }
                }
            }
            false // 继续遍历
        }
    }
    
    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        if (event.level.isClientSide) return
        // 当维度卸载时，确保所有实体物理被冻结
        freezeAllEntitiesInLevel(event.level as ServerLevel)
    }

    /**
     * 检查并更新实体的冻结状态
     */
    private fun checkAndUpdateEntityFreezeState(entity: Entity, playerPos: BlockPos? = null) {

        // 获取实体的物理体数量
        val bodies = entity.getAllBodies() ?: return
        if (bodies.isEmpty()) return
        
        // 当前冻结状态
        val isFrozen = entity.isPhysicsFrozen()
        val shouldFreeze = shouldEntityBePhysicsFrozen(entity, playerPos)
        
        // 如果状态需要变化，更新冻结状态
        if (isFrozen != shouldFreeze) {
            entity.setPhysicsFrozen(shouldFreeze)
            SparkCore.LOGGER.debug("更新实体物理冻结状态: ${entity.javaClass.simpleName} (${entity.id}) -> ${if (shouldFreeze) "冻结" else "解冻"}")
        }
    }
    
    /**
     * 判断实体是否应该被物理冻结
     */
    private fun shouldEntityBePhysicsFrozen(entity: Entity, playerPos: BlockPos? = null): Boolean {
        // 1. 检查实体状态 - 非活动实体应该被冻结
        if (!entity.isAlive) return true
        
        // 2. 检查距离 - 远离玩家的实体应该被冻结
        if (playerPos != null) {
            val distance = entity.blockPosition().distSqr(playerPos)
            val maxDistanceSq = FREEZE_DISTANCE * FREEZE_DISTANCE
            
            if (distance > maxDistanceSq) return true
            
            // 3. 检查可见性 - 简单实现：如果实体在玩家后方且距离超过一定值，则冻结
            if (entity.level().isClientSide) {
                val player = Minecraft.getInstance().player
                if (player != null) {
                    val playerLookVec = player.lookAngle
                    val playerToEntityVec = entity.position().subtract(player.position()).normalize()
                    val dotProduct = playerLookVec.dot(playerToEntityVec)
                    
                    // dotProduct < 0 表示实体在玩家视线后方
                    if (dotProduct < -0.5 && distance > maxDistanceSq / 4) return true
                }
            }
        } else {
            // 没有玩家参考点时，默认冻结
            return true
        }
        
        // 4. 检查实体类型优先级
        val priority = getEntityFreezePriority(entity)
        if (priority <= 1) {
            // 低优先级实体（如掉落物）在更短的距离内就冻结
            val playerEntity = if (entity.level().isClientSide) {
                Minecraft.getInstance().player
            } else {
                entity.level().players().firstOrNull()
            }
            
            if (playerEntity != null) {
                val distance = entity.distanceTo(playerEntity)
                if (distance > FREEZE_DISTANCE / 2) return true
            }
        }
        
        // 5. 检查特殊状态 - 如被石化、冰冻等游戏状态
        // 这里可以添加自定义的状态检查逻辑
        
        // 默认情况下不冻结
        return false
    }
    
    /**
     * 根据实体类型获取冻结优先级
     * 优先级越高，越晚被冻结
     */
    private fun getEntityFreezePriority(entity: Entity): Int {
        return when (
            // 玩家永远不会被冻结
            entity) {
            is net.minecraft.world.entity.player.Player -> Int.MAX_VALUE
            // 敌对生物优先级较高
            is net.minecraft.world.entity.monster.Monster -> 3
            // 友好生物优先级中等
            is net.minecraft.world.entity.animal.Animal -> 2
            // 掉落物优先级较低
            is net.minecraft.world.entity.item.ItemEntity -> 1
            // 其他实体最低优先级
            else -> 0
        }
    }
} 