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
import net.minecraft.world.level.Level

abstract class PhysLevel(
    val id: String,
    val name: String,
    open val level: Level,
    val tickStep: Long,
    val customApply: Boolean
) {

    val tickPreSecond = (1000 / tickStep).toInt()
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    val scope = CoroutineScope(newSingleThreadContext(name))
    val physWorld = PhysWorld(tickStep)
    protected var lastTickTime = System.nanoTime()
    protected val actions = mutableListOf<() -> Unit>()

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

    fun launch(action: () -> Unit) = actions.add(action)

    open fun physTick() {
        actions.forEach { it.invoke() }
        actions.clear()
        physWorld.physTick()
    }

}