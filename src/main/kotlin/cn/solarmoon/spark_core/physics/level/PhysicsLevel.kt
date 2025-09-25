package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.physics.BlockCollisionHelper
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.component.CollisionObjectComponent
import cn.solarmoon.spark_core.physics.component.CollisionObjectEvent
import cn.solarmoon.spark_core.physics.component.component
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import cn.solarmoon.spark_core.util.triggerEvent
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.ChunkAccess
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

abstract class PhysicsLevel(
    val name: String,
    open val mcLevel: Level
) : AutoCloseable, TaskSubmitOffice, PhysicsTickListener {

    companion object {
        const val TPS = 60
    }

    // 协程配置
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val scope = CoroutineScope(dispatcher + CoroutineName(name) + SupervisorJob() + CoroutineExceptionHandler(::handleException))

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
    val terrainChunks: ConcurrentHashMap<ChunkPos, ChunkAccess> = ConcurrentHashMap(32) //已加载的区块
    val terrainBlocks: HashSet<BlockPos> = HashSet()
    val terrainBlockBodies: ConcurrentHashMap<BlockPos, CollisionObjectComponent<PhysicsRigidBody>> = ConcurrentHashMap(1024) //已存在的地形块
    suspend fun CoroutineScope.run() {
        val fixedStep = 1f / TPS
        val repeat = TPS / 20

        while (isActive) {
            physicsTickChannel.receive()
            // 执行物理计算
            val ticker = System.nanoTime()
            stateFlow.value = PhysicsLevelState.RUNNING
            //TODO:根据负载压力调节步进频率，高负载时减少步进次数
            repeat(repeat) {
                tickCount++
                world.update(fixedStep, 0, false, true, false)
            }
            stateFlow.value = PhysicsLevelState.IDLE
            // 通知主线程计算完成
            lastStepTickTime = System.nanoTime() - ticker
            lastPhysicsTickTime = System.nanoTime()
            stepCompletedChannel.send(Unit)
        }
    }

    /**
     * 在主线程每tick调用，向物理线程发送模拟请求，物理线程接收到请求后会立刻模拟约1主线程tick时间的物理步进，此时主线程会继续执行后续内容
     */
    fun requestStep() {
        if (!::world.isInitialized) return

        // 保存当前tick的实体列表
        entities = requestEntities()

        // 如果物理线程已经在运行，发出警告并等待其完成
        if (overloadWarnCooldown > 0) overloadWarnCooldown--
        if (stateFlow.value == PhysicsLevelState.RUNNING) {
            if (overloadWarnCooldown <= 0) {
                SparkCore.LOGGER.warn(
                    "{} overloaded, last tick time: {}ms, rigid body in world: {}(part and entity)/{}(total)",
                    name,
                    (lastStepTickTime / 1000000).toInt(),
                    world.pcoList.size - terrainBlockBodies.size,
                    world.pcoList.size
                )
                overloadWarnCooldown = 40
            }
            return
        }
        terrainBlocks.clear()//清空潜在碰撞地形块列表
        world.pcoList.forEach {
            it.component?.tick()
            it.component?.triggerEvent(CollisionObjectEvent.Tick)
        }
        BlockCollisionHelper.addOrUpdateTerrainBlocksToWorld(terrainBlocks, this)//添加地形块
        // 发送物理步进请求（异步）
        scope.launch {
            physicsTickChannel.send(Unit)
        }
    }

    /**
     * 开启物理线程并初始化
     */
    fun start() {
        PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING) // 防止创建log刷屏
        scope.launch {
            world = PhysicsWorld(this@PhysicsLevel)
            run()
        }
    }

    /**
     * 关闭物理线程并清理资源
     */
    override fun close() {
        runBlocking {
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
            pco.component?.isColliding = false
            pco.component?.triggerEvent(CollisionObjectEvent.PhysicsTick.Pre())
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
            pco.component?.triggerEvent(CollisionObjectEvent.PhysicsTick.Post())
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