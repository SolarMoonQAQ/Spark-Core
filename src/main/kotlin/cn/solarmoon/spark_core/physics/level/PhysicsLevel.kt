package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.physics.collision.getBulletCollisionShape
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.util.TaskSubmitOffice
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
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.floor
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
    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    val previousTime = AtomicLong(System.nanoTime())

    override val taskMap: ConcurrentHashMap<String, () -> Unit> = ConcurrentHashMap()
    override val immediateQueue: ConcurrentLinkedDeque<() -> Unit> = ConcurrentLinkedDeque()

    //地形碰撞相关
    val terrainChunks: ConcurrentHashMap<ChunkPos, ChunkAccess> = ConcurrentHashMap(32) //已加载的区块
    val terrainBlocks: ConcurrentHashMap<BlockPos, BlockState> = ConcurrentHashMap(1024) //用于碰撞检测的地形块位置表
    val terrainBlockBodies: ConcurrentHashMap<BlockPos, PhysicsRigidBody> = ConcurrentHashMap(1024) //已存在的地形块
    val defaultShape = BoxCollisionShape(0.5f) //默认碰撞形状

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
        PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING) // 防止创建log刷屏
        world = PhysicsWorld(this@PhysicsLevel)

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
            // 同步清理资源
            withContext(physicsDispatcher) {
                terrainChunks.clear()
                terrainBlocks.clear()
                terrainBlockBodies.values.forEach { world.remove(it) }
                terrainBlockBodies.clear()
                world.destroy()
            }
            // 取消协程作用域
            scope.cancel()
        }
        SparkCore.LOGGER.info("绑定在 ${mcLevel.dimensionType().effectsLocation} 的物理线程 $name 已关闭")
    }

    fun addNearbyTerrainBlocksToWorld(pco: PhysicsCollisionObject) {
        val boundingBox = pco.boundingBox(null)
        val min = boundingBox.getMin(null)
        val max = boundingBox.getMax(null)
        if (pco is PhysicsRigidBody) {
            val v = pco.getLinearVelocity(null)//对于移动物体，额外向速度方向延伸判定区 Extend the detection area in the direction of the velocity for the moving object
            if (v.lengthSquared() < 1600) {//TODO:速度过大(40m/s+)的物体采用其他方法扩展选区 For objects with high speeds (40m/s+), use other methods to extend the selection area
                if (v.x < 0) min.x += v.x * 0.05f else max.x += v.x * 0.05f
                if (v.y < 0) min.y += v.y * 0.05f else max.y += v.y * 0.05f
                if (v.z < 0) min.z += v.z * 0.05f else max.z += v.z * 0.05f
            }
        }
        addTerrainBlocksToWorld(min, max)
    }

    fun addTerrainBlocksToWorld(min: Vector3f, max: Vector3f) {
        val minX = floor(min.x).toInt()
        val maxX = ceil(max.x).toInt()
        val minY = floor(min.y).toInt()
        val maxY = ceil(max.y).toInt()
        val minZ = floor(min.z).toInt()
        val maxZ = ceil(max.z).toInt()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val blockPos = BlockPos(x, y, z)
                    val chunkPos = ChunkPos(blockPos)
                    if (terrainChunks[chunkPos] != null) {
                        val blockState = terrainChunks[chunkPos]!!.getBlockState(blockPos)
                        if (terrainBlockBodies.containsKey(blockPos)) {//如果该位置的方块已经记录过，则检查方块类型后重置销毁倒计时 Reset the destruction count if the block has been recorded
                            if (blockState.isAir || blockState.getCollisionShape(mcLevel, blockPos).isEmpty) {
                                submitDeduplicatedTask(blockPos.toString()) { terrainBlockBodies[blockPos]?.setUserIndex(0) }
                            } else submitDeduplicatedTask(blockPos.toString()) { terrainBlockBodies[blockPos]?.setUserIndex(40) }
                        } else {//如果该位置的方块没有记录过，则获取块状态并创建刚体对象 Create a physics body for the block if it has not been recorded
                            if (!blockState.isAir && !blockState.getCollisionShape(mcLevel, blockPos).isEmpty) {
                                // 如果块不是空气或可替换方块，记录方块的状态和坐标 Record the block state and coordinates
                                terrainBlocks[blockPos] = blockState
                                submitDeduplicatedTask(blockPos.toString()) {
                                    val blockBody =
                                        PhysicsRigidBody(mcLevel, blockState.getBulletCollisionShape(), blockPos)
                                    blockBody.setUserIndex(40) //设定销毁倒计时(2秒，40主线程tick) Set the destruction count (2 seconds, 40 main thread ticks)
                                    blockBody.setPhysicsLocation(
                                        Vector3f(
                                            blockPos.x.toFloat() + 0.5f,
                                            blockPos.y.toFloat() + 0.5f,
                                            blockPos.z.toFloat() + 0.5f
                                        )
                                    )
                                    blockBody.mcTickPos = blockBody.getPhysicsLocation(null)
                                    blockBody.lastMcTickPos = blockBody.mcTickPos
                                    terrainBlockBodies[blockPos] = blockBody
                                    world.add(blockBody)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun prePhysicsTick(space: PhysicsSpace?, timeStep: Float) {
        world.pcoList.forEach { pco ->
            pco.isColliding = false
            pco.tickers.forEach { it.prePhysicsTick(pco, this) }
            if (!pco.isStatic && pco.owner != mcLevel && !pco.name.equals("terrain")) {
                addNearbyTerrainBlocksToWorld(pco)
            }
        }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Pre(this))
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        tickCount++

        world.pcoList.forEach { pco ->
            pco.tickers.forEach { it.physicsTick(pco, this) }
            if (!mcLevel.isClientSide) {
                mcLevel.submitDeduplicatedTask("updateState: ${pco.nativeId()}") {
                    pco.sync.update()
                }
            }
        }

        terrainBlockBodies.forEach { (pos, body) ->
            if (body.userIndex() <= 0) {//移除过久未被访问的块记录及其刚体对象
                terrainBlocks.remove(pos)
                world.remove(body)
                terrainBlockBodies.remove(pos)
            } else body.setUserIndex(body.userIndex() - 1) //销毁倒计时推进
        }

        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent(this))
        processTasks()
        lastPhysicsTickTime = System.nanoTime()
    }

}