package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsTickEvent
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsSpace.BroadphaseType
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

abstract class PhysicsLevel(
    val name: String,
): PhysicsTickListener, AutoCloseable {

    companion object {
        const val TPS = 60
    }

    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    val previousTime = AtomicLong(System.nanoTime())

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val physicsDispatcher = newSingleThreadContext(name).apply {
        executor.execute {
            Thread.currentThread().apply {
                priority = Thread.NORM_PRIORITY
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                    SparkCore.LOGGER.error("物理线程 #$name 崩溃了", e)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(physicsDispatcher)

    private val lazyWorld = lazy { PhysicsSpace(
        Vector3f(-Int.MAX_VALUE.toFloat(), -10_000f, -Int.MAX_VALUE.toFloat()),
        Vector3f(Int.MAX_VALUE.toFloat(), 10_000f, Int.MAX_VALUE.toFloat()),
        BroadphaseType.DBVT
    ) }

    val world get() = lazyWorld.value

    abstract val mcLevel: Level

    init {
        // 防止创建log刷屏
        PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING)
        scope.launch {
            lazyWorld.value.apply {
                setGravity(Vector3f(0f, -9.81f, 0f))
                addTickListener(this@PhysicsLevel)
            }
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
        val maxSubSteps = 10         // 最大允许每帧子步数
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
                    world.update(steps * fixedTimeStep, steps)
                } catch (e: Exception) {
                    accumulatedTime = 0f // 重置累积时间防止雪崩
                    SparkCore.LOGGER.error("绑定在 ${mcLevel.dimensionType().effectsLocation} 的物理线程 $name 更新步长失败，已重置时间累计", e)
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
            // 第一阶段：停止协程
            scope.coroutineContext.cancelChildren()

            // 第二阶段：同步清理物理世界
            withContext(physicsDispatcher) {
                // 清理物理世界
                world.destroy()
                SparkCore.LOGGER.info("绑定在 ${mcLevel.dimensionType().effectsLocation} 的物理世界已清理")
            }

            // 第三阶段：关闭线程
            physicsDispatcher.close()
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

    override fun prePhysicsTick(space: PhysicsSpace?, timeStep: Float) {
        NeoForge.EVENT_BUS.post(PhysicsTickEvent.Level.Pre(this))
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        NeoForge.EVENT_BUS.post(PhysicsTickEvent.Level(this))
    }

    val partialTicks: Float get() {
        val currentTime = System.nanoTime()
        val deltaTimeMs = (currentTime - previousTime.get()) / 1_000_000.0
        val tickTimeMs = 1000f / TPS
        return (deltaTimeMs.toFloat() / tickTimeMs).coerceIn(0f, 1f)
    }

}