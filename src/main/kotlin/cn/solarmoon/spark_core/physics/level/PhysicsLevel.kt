package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.objects.PhysicsRigidBody
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

abstract class PhysicsLevel(
    val name: String,
    open val mcLevel: Level
): AutoCloseable, TaskSubmitOffice, PhysicsTickListener {

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

    // 同步控制
    private val simulationLock = Mutex()
    private val physicsTickChannel = Channel<Unit>(Channel.RENDEZVOUS)
    private val stepCompletedChannel = Channel<Unit>(Channel.RENDEZVOUS)

    // 运行时数据
    lateinit var world: PhysicsWorld
        private set
    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    override val taskMap: ConcurrentHashMap<String, Pair<PPhase, () -> Unit>> = ConcurrentHashMap()
    override val immediateQueue: ConcurrentLinkedDeque<Pair<PPhase, () -> Unit>> = ConcurrentLinkedDeque()

    suspend fun CoroutineScope.run() {
        while (isActive) {
            physicsTickChannel.receive()

            // 执行物理计算
            stateFlow.value = PhysicsLevelState.RUNNING
            repeat(TPS / 20) {
                world.update(1f/TPS, 0, false, true, false)
            }
            stateFlow.value = PhysicsLevelState.IDLE

            // 通知主线程计算完成
            stepCompletedChannel.send(Unit)
        }
    }

    /**
     * 在主线程每tick调用，向物理线程发送模拟请求，物理线程接收到请求后会立刻模拟约1主线程tick时间的物理步进，此时主线程会等待物理线程计算完毕后再执行后续内容
     */
    fun requestStep() {
        runBlocking {
            simulationLock.withLock {
                physicsTickChannel.send(Unit)
                stepCompletedChannel.receive() // 等待物理线程完成
            }
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
            world.destroy()
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
            pco.tickers.forEach { it.prePhysicsTick(pco, this) }
        }
        processTasks(PPhase.PRE)
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        world.pcoList.forEach { pco ->
            pco.tickers.forEach { it.postPhysicsTick(pco, this) }
        }
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

}