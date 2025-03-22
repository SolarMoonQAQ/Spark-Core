package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import cn.solarmoon.spark_core.physics.terrain.TerrainManager
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

abstract class PhysicsLevel(
    val name: String,
) : PhysicsTickListener, AutoCloseable, TaskSubmitOffice {

    companion object {
        const val TPS = 60
    }

    var tickCount = 0L
        private set
    var lastPhysicsTickTime = System.nanoTime()
        private set
    var lastMcTickTime = System.nanoTime()
        private set
    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    val previousTime = AtomicLong(System.nanoTime())

    //地形碰撞相关
    val terrainBlocks: ConcurrentHashMap<BlockPos, BlockState> = ConcurrentHashMap(1024) //用于碰撞检测的地形块位置表
    val terrainBlockBodies: ConcurrentHashMap<BlockPos, PhysicsRigidBody> = ConcurrentHashMap(1024) //已存在的地形块
    val terrainChunks: ConcurrentHashMap<ChunkPos, ChunkAccess> = ConcurrentHashMap(32) //已加载的区块
//    private val defaultShape = BoxCollisionShape(0.5f) //默认碰撞形状

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val physicsDispatcher = newSingleThreadContext(name).apply {
        executor.execute {
            Thread.currentThread().apply {
                priority = Thread.NORM_PRIORITY
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                    throw RuntimeException("物理线程 $name 崩溃了", e)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(physicsDispatcher)

    lateinit var world: PhysicsWorld
        private set

    abstract val mcLevel: Level

    // 地形管理器
    internal val terrainManager = TerrainManager(this)

    private var tickCounter = 0

    init {
        // 防止创建log刷屏
        PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING)
        scope.launch {
            world = PhysicsWorld(this@PhysicsLevel)
        }
    }

    /**
     * 物理模拟协程的核心循环流程说明
     *
     * ## 执行流程图
     *
     * ```
     * 循环开始 (isActive = true)
     * │
     * ├─ [测量时间差] elapsed = (currentTime - previousTime) / 1e9f
     * ├─ [时间累积] accumulatedTime += elapsed
     * │
     * ├─ [计算子步数] steps = min(accumulatedTime / fixedTimeStep, maxSubSteps)
     * │   └─ 限制最大子步防止卡顿
     * │
     * ├─ [条件分支] steps > 0?
     * │   ├─ 是 →
     * │   │   ├─ [执行物理更新] update(timeInterval, maxSteps)
     * │   │   │   ├─ timeInterval = steps * fixedTimeStep (总处理时长)
     * │   │   │   └─ maxSteps = steps (实际允许步数)
     * │   │   └─ [扣除已处理时间] accumulatedTime -= timeInterval
     * │   └─ 否 → 跳过物理计算
     * │
     * ├─ [计算帧时间] sleepTime = max(0, targetFrameTime - elapsedFrameTime)
     * └─ [精确等待] delay(sleepTime毫秒)
     * ```
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() = scope.launch {
        val fixedTimeStep = 1f / TPS // 固定时间步长（秒）
        val maxSubSteps = 10 // 最大允许每帧子步数
        var accumulatedTime = 0f     // 累积时间（秒）

        while (isActive) {

            val currentTime = System.nanoTime()
            val elapsed = (currentTime - previousTime.get()) / 1e9f // 转换为秒
            previousTime.set(currentTime)
            accumulatedTime += elapsed

            // 计算本帧最多能执行多少子步
            val steps = min((accumulatedTime / fixedTimeStep).toInt(), maxSubSteps)

            if (steps > 0) {
                // 执行物理更新
                try {
                    world.update(steps * fixedTimeStep, steps, true, true, true)
                } catch (e: Exception) {
                    accumulatedTime = 0f // 重置累积时间防止雪崩
                    SparkCore.LOGGER.error(
                        "绑定在 ${mcLevel.dimensionType().effectsLocation} 的物理线程 $name 更新步长失败，已重置时间累计",
                        e
                    )
                }

                // 扣除已处理的时间（保留残余时间）
                accumulatedTime -= steps * fixedTimeStep
            }

            // 计算剩余帧时间（保证60Hz频率）
            val targetFrameTime = 1f / TPS
            val elapsedFrameTime = (System.nanoTime() - currentTime) / 1e9f
            val sleepTime = max(0f, targetFrameTime - elapsedFrameTime)

            delay((sleepTime * 1000).toLong()) // 转换为毫秒
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun close() {
        runBlocking {
            // 第二阶段：同步清理物理世界
            withContext(physicsDispatcher) {
                // 第一阶段：停止协程
                terrainBlocks.clear()
                terrainBlockBodies.values.forEach { world.remove(it) }
                terrainBlockBodies.clear()
                // 清理地形管理器
                terrainManager.cleanup()
                // 清理物理世界
                world.destroy()
            }
            // 第三阶段：关闭线程
            scope.cancel()
            SparkCore.LOGGER.info("绑定在 ${mcLevel.dimensionType().effectsLocation} 的物理线程 $name 已关闭")
        }
    }



    /**
     * 提交任务到物理线程执行
     */
    fun submitTask(block: suspend CoroutineScope.() -> Unit) =
        scope.launch {
            block()
        }

    fun mcTick() {
        tickCounter++
        world.pcoList.iterator().forEach { pco ->
            pco.mcTickPos?.let { pco.lastMcTickPos = it }
            pco.mcTickPos = pco.getPhysicsLocation(null)
        }
        lastMcTickTime = System.nanoTime()
        // 清理过期的地形碰撞体
        terrainManager.clearExpiredBodies()
    }


    override fun prePhysicsTick(space: PhysicsSpace?, timeStep: Float) {
        world.pcoList.iterator().forEach { pco ->
            pco.isColliding = false
        }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Pre(this))
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        tickCount++

        world.pcoList.forEach { pco ->
            pco.tickers.forEach { it.physicsTick(pco, this) }
        }

        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent(this))
        processTasks()
        lastPhysicsTickTime = System.nanoTime()
    }

    val partialTicks: Float
        get() {
            val currentTime = System.nanoTime()
            val elapsedSinceLastTick = (currentTime - lastPhysicsTickTime) / 1e9f
            return (elapsedSinceLastTick * TPS).coerceIn(0f, 1f)
        }

    val mcPartialTicks: Float
        get() {
            val currentTime = System.nanoTime()
            val elapsedSinceLastTick = (currentTime - lastMcTickTime) / 1e9f
            return (elapsedSinceLastTick * 20).coerceIn(0f, 1f)
        }

    /**
     * 处理方块状态变化
     */
    fun onBlockChanged(pos: BlockPos, newState: BlockState) {
        terrainManager.onBlockChanged(pos, newState)
    }


    /**
     * 获取性能报告
     */
    fun getPerformanceReport(): String {
        return terrainManager.getPerformanceReport()
    }

}