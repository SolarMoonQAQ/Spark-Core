package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.phys.PhysWorld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

abstract class PhysLevel(
    val id: ResourceLocation,
    val name: String,
    open val level: Level,
    val tickStep: Long,
    val customApply: Boolean
) {

    val tickPreSecond = (1000 / tickStep).toInt()
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    val scope = CoroutineScope(newSingleThreadContext(name))
    val world = PhysWorld(tickStep)
    protected var lastTickTime = System.nanoTime()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() {
        scope.launch {
            while (isActive) {
                val startTime = System.nanoTime()

                physTick()

                val endTime = System.nanoTime()
                lastTickTime = endTime
                val executionTime = (endTime - startTime) / 1_000_000
                val remainingDelay = tickStep - executionTime
                if (remainingDelay > 0) {
                    delay(remainingDelay)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun unLoad() {
        scope.cancel()
    }

    open fun physTick() {
        world.physTick()
    }

    val partialTicks: Double get() {
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastTickTime) / 1_000_000.0
        return (deltaTime / tickStep).coerceIn(0.0, 1.0)
    }

}