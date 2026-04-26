package cn.solarmoon.spark_core.compat.create

import cn.solarmoon.spark_core.api.physicsLevel
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import cn.solarmoon.spark_core.physics.body.addPhysicsBody
import cn.solarmoon.spark_core.physics.body.removePhysicsBody
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.Contraption
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Create 装置物理联动事件处理器。
 *
 * 关键设计：
 * 1) 以 [PhysicsLevel] 分桶存储宿主，避免单人游戏下客户端/服务端同 ID 串线；
 * 2) `Contraption -> Host` 使用弱键映射，服务于 Mixin 的脏标记回调；
 * 3) 生命周期事件与物理同步事件均只处理当前 level 对应分桶。
 */
object CreateContraptionPhysicsApplier {

    /**
     * 分桶存储：PhysicsLevel -> (entityId -> host)。
     *
     * 说明：
     * - 客户端 PhysicsLevel 与服务端 PhysicsLevel 为不同实例；
     * - 即便 entityId 相同，也会落在不同桶中，天然隔离。
     */
    private val hostsByLevel =
        ConcurrentHashMap<PhysicsLevel, ConcurrentHashMap<Int, CreateContraptionPhysicsHost>>()

    /**
     * Contraption -> Host（弱键）。
     *
     * 用于在 `Contraption.invalidateColliders()` 时快速将对应宿主标记为脏。
     */
    private val hostsByContraption =
        Collections.synchronizedMap(WeakHashMap<Contraption, CreateContraptionPhysicsHost>())

    /**
     * 获取（或创建）指定 physics level 的宿主分桶。
     */
    private fun bucket(level: PhysicsLevel): ConcurrentHashMap<Int, CreateContraptionPhysicsHost> {
        return hostsByLevel.computeIfAbsent(level) { ConcurrentHashMap() }
    }

    /**
     * 绑定 Contraption 与宿主对象映射。
     *
     * 由 Host 在检测到装置切换时调用。
     */
    fun bindContraption(contraption: Contraption, host: CreateContraptionPhysicsHost) {
        hostsByContraption[contraption] = host
    }

    /**
     * 供 Mixin 调用：Create 通知碰撞器失效时，将宿主标记为脏。
     */
    fun markDirty(contraption: Contraption) {
        hostsByContraption[contraption]?.markShapeDirty()
    }

    /**
     * 装置进入世界时创建并挂载运动学刚体。
     */
    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        if (!ModList.get().isLoaded(CreateCompat.MOD_ID)) return
        val entity = event.entity as? AbstractContraptionEntity ?: return
        val level = event.level ?: return
        val physicsLevel = level.physicsLevel

        val host = bucket(physicsLevel).computeIfAbsent(entity.id) {
            CreateContraptionPhysicsHost(physicsLevel)
        }
        host.bindEntity(entity)

        // 若当前已有 contraption，提前绑定并打脏，保证下一同步周期重建形状
        entity.contraption?.let {
            bindContraption(it, host)
            host.markShapeDirty()
        }

        // 同一 host 只允许一次入世界，避免重复添加碰撞体
        if (!host.body.isInWorld) {
            physicsLevel.mcLevel.addPhysicsBody(host.body)
        }
    }

    /**
     * 装置离开世界时移除刚体并清理映射。
     */
    @SubscribeEvent
    fun onEntityLeave(event: EntityLeaveLevelEvent) {
        if (!ModList.get().isLoaded(CreateCompat.MOD_ID)) return
        val entity = event.entity as? AbstractContraptionEntity ?: return
        val level = event.level ?: return
        val physicsLevel = level.physicsLevel

        val levelBucket = hostsByLevel[physicsLevel] ?: return
        val host = levelBucket.remove(entity.id) ?: return

        host.currentContraption?.let { hostsByContraption.remove(it) }
        entity.contraption?.let { hostsByContraption.remove(it) }
        if (host.body.isInWorld) {
            physicsLevel.mcLevel.removePhysicsBody(host.body)
        }
    }

    /**
     * 主线程同步周期回调
     *
     * 该事件由 SparkCore 在主线程 pre-tick 阶段抛出，
     * 这里按“实体所在 level 的 physicsLevel 分桶”定位 host，避免跨端误命中
     */
    @SubscribeEvent
    fun onEntityTick(event: EntityTickEvent.Pre) {
        if (!ModList.get().isLoaded(CreateCompat.MOD_ID)) return
        val entity = event.entity as? AbstractContraptionEntity ?: return
        //移除SparkCore为实体添加的默认碰撞箱刚体
        val body: PhysicsCollisionObject? = entity.getPhysicsBody("body")
        if (body != null) {
            entity.level().removePhysicsBody(body)
        }
        val physicsLevel = entity.level().physicsLevel
        val host = hostsByLevel[physicsLevel]?.get(entity.id) ?: return
        host.onSyncTick(entity)
    }

    /**
     * 主线程同步周期回调
     *
     * 该事件由 SparkCore 在主线程 pre-tick 阶段抛出，
     * 这里按“实体所在 level 的 physicsLevel 分桶”定位 host，避免跨端误命中
     */
    @SubscribeEvent
    fun onPhysicsEntityTick(event: PhysicsEntityTickEvent) {
        if (!ModList.get().isLoaded(CreateCompat.MOD_ID)) return
        val entity = event.entity as? AbstractContraptionEntity ?: return
        val physicsLevel = entity.level().physicsLevel
        val host = hostsByLevel[physicsLevel]?.get(entity.id) ?: return
        host.onPhysicsTick(entity)
    }

    /**
     * 世界卸载时清理对应分桶
     *
     * 说明：
     * - 该清理用于兜底防泄漏（例如异常流程下未触发完整实体离场事件）；
     * - 只处理当前卸载 level 对应的 physicsLevel，不影响其他维度/端
     */
    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        if (!ModList.get().isLoaded(CreateCompat.MOD_ID)) return
        val level = event.level as? Level ?: return
        val physicsLevel = level.physicsLevel
        val levelBucket = hostsByLevel.remove(physicsLevel) ?: return

        levelBucket.values.forEach { host ->
            host.currentContraption?.let { hostsByContraption.remove(it) }
            if (host.body.isInWorld) {
                physicsLevel.mcLevel.removePhysicsBody(host.body)
            }
        }
        levelBucket.clear()
    }
}
