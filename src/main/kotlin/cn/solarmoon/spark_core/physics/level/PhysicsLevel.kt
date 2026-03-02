package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.body.*
import cn.solarmoon.spark_core.physics.terrain.BlockShapeManager
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection
import cn.solarmoon.spark_core.util.*
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.joints.New6Dof
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

abstract class PhysicsLevel(
    val name: String,
    open val mcLevel: Level,
    open val baseStep: Int = 5,
) : AutoCloseable, TaskSubmitOffice, PhysicsTickListener {

    companion object {
        /** 最小允许步进次数 */
        const val MIN_STEP = 1

        /** 主线程tick预算时间（纳秒），MC为20Hz → 50ms，留出一些线程调度和数据同步余量 */
        const val TICK_BUDGET_NS = 45_000_000L

        /** 降频阈值：超过预算的比例 */
        const val OVERLOAD_THRESHOLD_RATIO = 0.8  // 80%预算开始降频

        /** 升频阈值：低于预算的比例 */
        const val RECOVER_THRESHOLD_RATIO = 0.4  // 40%预算以下尝试升频

        /** 快速响应系数：负载上升时极快靠近新值 */
        private const val ATTACK_ALPHA = 0.95

        /** 缓慢恢复系数：负载下降时极慢回落 */
        private const val DECAY_ALPHA = 0.01
    }

    val tps = baseStep * 20

    /** 当前动态步进次数 */
    @Volatile
    private var dynamicRepeat = baseStep

    /** 平滑步进时间：过去约n跳的平均值 */
    private var smoothedStepTime = TICK_BUDGET_NS.toDouble()

    /** 调整冷却：修改频率后至少等待多少tick才能再次修改 */
    private var adjustmentCooldown = 0

    // 协程配置
    val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply {
            isDaemon = true
        }
    }.asCoroutineDispatcher()
    val scope =
        CoroutineScope(dispatcher + CoroutineName(name) + SupervisorJob() + CoroutineExceptionHandler(::handleException))

    // 状态管理
    private val stateFlow = MutableStateFlow(PhysicsLevelState.IDLE)
    val state = stateFlow.asStateFlow()
    private val crashCount = AtomicInteger(0)
    var tickCount: Int = 0

    // 同步控制
    private val physicsTickChannel = Channel<Unit>(Channel.CONFLATED)
    private val stepCompletedChannel = Channel<Unit>(Channel.CONFLATED)

    // 运行时数据
    lateinit var world: PhysicsWorld
        private set
    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    override val taskMap = ConcurrentHashMap<PPhase, ConcurrentHashMap<String, () -> Unit>>()
    override val immediateQueue = ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<() -> Unit>>()
    var lastPhysicsTickTime = System.nanoTime()
    var lastStepTickTime = 0L
    var overloadWarnCooldown = 0

    var entities = listOf<Entity>()
        private set

    //地形碰撞相关
    lateinit var terrainManager: PhysicsChunkManager
    lateinit var blockShapeManager: BlockShapeManager

    suspend fun CoroutineScope.run() {
        val fixedStep = 1f / tps
        while (isActive) {
            physicsTickChannel.receive()
            // 执行物理计算
            val ticker = System.nanoTime()
            stateFlow.value = PhysicsLevelState.RUNNING
            // 动态步进次数
            repeat(dynamicRepeat) {
                tickCount++
                world.update(fixedStep, 0, false, true, false)
            }
            // 统计耗时
            lastStepTickTime = System.nanoTime() - ticker
            lastPhysicsTickTime = System.nanoTime()
            // ===== 动态调节逻辑 =====
            val currentMs = lastStepTickTime.toDouble()
            smoothedStepTime = if (currentMs > smoothedStepTime) {
                // 负载上升：快速跟随 (Attack)
                (ATTACK_ALPHA * currentMs) + (1.0 - ATTACK_ALPHA) * smoothedStepTime
            } else {
                // 负载下降：缓慢回落 (Decay)
                (DECAY_ALPHA * currentMs) + (1.0 - DECAY_ALPHA) * smoothedStepTime
            }
            if (adjustmentCooldown > 0) {
                adjustmentCooldown--
            } else {
                val overloadThreshold = (TICK_BUDGET_NS * OVERLOAD_THRESHOLD_RATIO).toLong()
                val recoveryThreshold = (TICK_BUDGET_NS * RECOVER_THRESHOLD_RATIO).toLong()
                when {
                    smoothedStepTime > overloadThreshold && dynamicRepeat > MIN_STEP -> {
                        dynamicRepeat--
                        adjustmentCooldown = 20 // 降频后观察1秒（20tick）再做决定
                        SparkCore.LOGGER.warn(
                            "{} physics overload detected, step reduced to {}",
                            name,
                            dynamicRepeat
                        )
                    }

                    smoothedStepTime < recoveryThreshold && dynamicRepeat < baseStep -> {
                        dynamicRepeat++
                        adjustmentCooldown = 40 // 升频需要更谨慎，多观察一会儿
                        SparkCore.LOGGER.warn(
                            "{} physics recovered, step increased to {}",
                            name,
                            dynamicRepeat
                        )
                    }
                }
            }
            // 通知主线程计算完成
            stateFlow.value = PhysicsLevelState.IDLE
            stepCompletedChannel.send(Unit)
        }
    }

    /**
     * 在主线程每tick调用，向物理线程发送模拟请求，物理线程接收到请求后会立刻模拟约1主线程tick时间的物理步进，此时主线程会继续执行后续内容
     */
    fun requestStep() {
        if (!::world.isInitialized) return
        // 如果物理线程已经在运行，跳过此次同步等待其完成
        if (stateFlow.value == PhysicsLevelState.RUNNING) {
            return
        }
        // 警告信息
        if (overloadWarnCooldown > 0) overloadWarnCooldown--
        if ((lastStepTickTime / 1000000) >= 50) {
            SparkCore.LOGGER.warn(
                "{} tick {} overloaded, last tick time: {}ms, speed: {}%, rigid body in world: {} with {}, while {} chunks loaded.",
                name,
                tickCount,
                (lastStepTickTime / 1000000).toInt(),
                dynamicRepeat / baseStep * 100,
                world.pcoList.size,
                terrainManager.getStats(),
                mcLevel.chunkSource.loadedChunksCount
            )
            overloadWarnCooldown = 40
        }
        // 保存当前tick的实体列表
        entities = requestEntities()
        // 更新世界刚体状态快照
        // 1️⃣ 结构同步（极少发生）
        world.worldSnapshot.syncStructure()
        // 2️⃣ transform 同步（每 tick）
        world.worldSnapshot.syncTransform()

        // 收集所有需要激活地形的刚体的包围盒
        val buildBoxes = mutableListOf<AABB>()
        val activationBoxes = mutableListOf<AABB>()

        // 遍历所有刚体，更新其状态
        world.pcoList.forEach {
            it.triggerEvent(PhysicsBodyEvent.Tick)
            stateOf(it).update()
            // 收集所有需要激活地形的刚体的包围盒
            val owner = it.owner
            if (!it.isStatic && owner !is PhysicsChunkSection) {
                if (it.collideWithGroups and CollisionGroups.TERRAIN != 0 || owner is Player)
                    if (owner !is RigidBodyEntity || (owner.isActive)) {
                        var aabb = stateOf(it).cachedBoundingBox.toAABB()
                        if (it is PhysicsRigidBody) {
                            val delta = it.getLinearVelocity(null).toVec3().scale(1.5 / tps)
                            if (delta.length() < 5f)
                                aabb = aabb.expandTowards(delta)
                        }
                        if (owner is Player) aabb = aabb.expandTowards(0.0, -1.1, 0.0)
                        buildBoxes.add(aabb)
                        activationBoxes.add(aabb)
                    }
            }
        }

        // 统一更新地形
        terrainManager.updateDirtySections()
        terrainManager.updateBuild(activationBoxes)
        terrainManager.updateActivation(activationBoxes)
//        if (world.pcoList.isNotEmpty()) SparkCore.LOGGER.debug("tick: " + tickCount + ", " + terrainManager.getStats())
        // 发送物理步进请求（异步）
        scope.launch {
            physicsTickChannel.send(Unit)
        }
    }

    /**
     * 开启物理线程并初始化
     */
    fun start(onInitialized: (() -> Unit)? = null) {
        PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING) // 防止创建log刷屏
        New6Dof.logger2.setLevel(java.util.logging.Level.WARNING)
        SparkCore.LOGGER.info(
            "启动物理线程：{}，线程数：{}/{}, threadSafe:{}, Debug:{}",
            name,
            Runtime.getRuntime().availableProcessors(),
            NativeLibrary.countThreads(),
            NativeLibrary.isThreadSafe(),
            NativeLibrary.isDebug()
        )
        scope.launch {
            world = PhysicsWorld(this@PhysicsLevel)
            terrainManager = PhysicsChunkManager(this@PhysicsLevel)
            blockShapeManager = BlockShapeManager(this@PhysicsLevel)

            // 初始化完成，执行回调
            onInitialized?.invoke()

            run()
        }
    }

    /**
     * 关闭物理线程并清理资源
     */
    override fun close() {
        runBlocking {
            if (::terrainManager.isInitialized) terrainManager.destroy()
            if (::blockShapeManager.isInitialized) {
                blockShapeManager.SHAPE_CACHE.clear()
            }
            if (::world.isInitialized) world.destroy()
            hostManager.clear()
            scope.cancel("物理线程已关闭")
            dispatcher.close()
        }
    }

    /**
     * 重启并刷新线程
     */
    fun restart() {
        close()
        start()
        crashCount.set(0)
    }

    override fun prePhysicsTick(space: PhysicsSpace, timeStep: Float) {
        world.pcoList.forEach { pco ->
            pco.isColliding = false
            pco.triggerEvent(PhysicsBodyEvent.PhysicsTick.Pre())
        }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Pre(this))
        processTasks(PPhase.ALL)
        processTasks(PPhase.PRE)

        entities.forEach {
            NeoForge.EVENT_BUS.post(PhysicsEntityTickEvent(it))
        }
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        world.pcoList.forEach { pco ->
            pco.triggerEvent(PhysicsBodyEvent.PhysicsTick.Post())
        }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Post(this))
        processTasks(PPhase.ALL)
        processTasks(PPhase.POST)
    }

    /**
     * 异常处理
     */
    protected fun handleException(context: CoroutineContext, exception: Throwable) {
        SparkCore.LOGGER.error("物理线程崩溃！尝试重启...", exception)
        if (crashCount.incrementAndGet() < 3) {
            restart()
        } else {
            SparkCore.LOGGER.error("物理线程连续崩溃，停止恢复！")
        }
    }

    abstract fun requestEntities(): List<Entity>

}