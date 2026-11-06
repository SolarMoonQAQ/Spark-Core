package cn.solarmoon.spark_core.animation.anim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AnimControllerConcurrencyTest {
    @Test
    fun tickToleratesAnotherThreadCreatingAnAnimationGroup() {
        val holder = StubAnimatable()
        val controller = holder.animController
        val first = instance(holder, anim()).apply { group = 10 }
        val later = instance(holder, anim()).apply { group = 30 }
        val incoming = instance(holder, anim()).apply { group = 20 }
        controller.playAnimation(first)
        controller.playAnimation(later)
        var laterTicks = 0
        later.onEvent<AnimEvent.Tick> { laterTicks++ }

        first.onEvent<AnimEvent.Tick> {
            // tick 已经访问第一层；让另一线程插入新层后再继续遍历。
            CompletableFuture.runAsync { controller.playAnimation(incoming) }.get(5, TimeUnit.SECONDS)
        }

        controller.tick()

        assertEquals(listOf(10, 20, 30), controller.layers.keys.toList())
        assertEquals(listOf(incoming), controller.layers.getValue(20).snapshotAnimations())
        assertEquals(1, laterTicks)
    }
}
