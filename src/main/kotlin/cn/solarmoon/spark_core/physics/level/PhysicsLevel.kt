package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.physics.collision.BlockCollisionHelper
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.minecraft.core.BlockPos
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
    lateinit var worldSnapshot: PhysicsWorld
        private set
    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    override val taskMap = ConcurrentHashMap<PPhase, ConcurrentHashMap<String, () -> Unit>>()
    override val immediateQueue = ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<() -> Unit>>()
    var lastPhysicsTickTime = System.nanoTime()
    var lastStepTickTime = 0L
    var overloadWarnCooldown = 0

    //地形碰撞相关
    val terrainChunks: ConcurrentHashMap<ChunkPos, ChunkAccess> = ConcurrentHashMap(32) //已加载的区块
    val terrainBlockBodies: ConcurrentHashMap<BlockPos, PhysicsRigidBody> = ConcurrentHashMap(1024) //已存在的地形块
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
            //TODO:移除方块碰撞体时有概率崩溃，考虑在physicsTick中移除
//            // 使用runBlocking在主线程安全地等待物理线程计算完成
//            runBlocking {
//                try {
//                    withTimeout(10000) { // 最多等待10秒
//                        stepCompletedChannel.receive()
//                    }
//                } catch (e: Exception) {
//                    if (e is TimeoutCancellationException) {
//                        if (tickCount < 60) return@runBlocking
//                    }
//                    throw e
//                }
//            }
        }
        // 更新所有物体的位置姿态信息，处理地形碰撞
        val tp = Transform()
        world.pcoList.forEach {
//            if (it is PhysicsRigidBody) {//将所有刚体信息记录至快照中
//                if (it.snapShotTwin == null) {
//                    val snapShot = PhysicsRigidBody(it)
//                    worldSnapshot.addCollisionObject(snapShot)
//                }
//                it.snapShotTwin.get()?.apply {
//                    setPhysicsTransform(it.getTransform(null))
//                    setLinearVelocity(it.getLinearVelocity(null))
//                    setAngularVelocity(it.getAngularVelocity(null))
//                }
//            }
            if (!it.isStatic) {//仅更新非静态的物体
                if (!it.isActive) return@forEach
                it.lastTickTransform = it.tickTransform.clone()
                it.tickTransform.apply {
                    val t = it.getTransform(tp)
                    translation.set(t.translation)
                    rotation.set(t.rotation)
                    scale.set(t.scale)
                }
                try {
                    it.boundingBox(it.cachedBoundingBox)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("{}碰撞箱计算结果异常：", it.name, e)
                }
                if ((it.collideWithGroups and PhysicsCollisionObject.COLLISION_GROUP_BLOCK != 0))
                    BlockCollisionHelper.addNearbyTerrainBlocksToWorld(it, this@PhysicsLevel)
            } else if (it.collisionGroup == PhysicsCollisionObject.COLLISION_GROUP_BLOCK && it is PhysicsRigidBody) {
                if (it.userIndex() <= 0) {//移除过久未被访问的块记录及其刚体对象
                    world.remove(it)
                    terrainBlockBodies.remove(it.blockPos)
                } else it.setUserIndex(it.userIndex() - 1) //销毁倒计时推进
            }
        }
//        worldSnapshot.pcoList.forEach { //清理快照中已经于物理世界被删除的对象
//            if (it is PhysicsRigidBody) {
//                if (it.snapShotTwin.get() == null || !world.pcoList.contains(it.snapShotTwin.get())) {
//                    worldSnapshot.remove(it)
//                    it.snapShotTwin = null
//                }
//            }
//        }
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
            worldSnapshot = PhysicsWorld(this@PhysicsLevel, 1)
            run()
        }
    }

    /**
     * 关闭物理线程并清理资源
     */
    override fun close() {
        runBlocking {
            world.destroy()
            worldSnapshot.destroy()
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
            pco.tickers.forEach { it.prePhysicsTick(pco, this) }
        }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Pre(this))
        processTasks(PPhase.PRE)
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        world.pcoList.forEach { pco ->
            pco.tickers.forEach { it.postPhysicsTick(pco, this) }
            pco.tickCount++
        }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Post(this))
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