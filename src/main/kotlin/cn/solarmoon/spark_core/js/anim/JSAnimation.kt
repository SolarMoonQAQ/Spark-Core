package cn.solarmoon.spark_core.js.anim

import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

class JSAnimation(
    val anim: AnimInstance
) {

    @HostAccess.Export
    fun getTime() = anim.time

    @HostAccess.Export
    fun getSpeed() = anim.speed

    @HostAccess.Export
    fun getName() = anim.name

    @HostAccess.Export
    fun getProgress() = anim.getProgress()

    @HostAccess.Export
    fun onSwitchIn(consumer: Value) {
        anim.onEvent<AnimEvent.SwitchIn> {
            val p = it.previous
            consumer.execute(p?.let { JSAnimation(it) })
        }
    }

    @HostAccess.Export
    fun onSwitchOut(consumer: Value) {
        anim.onEvent<AnimEvent.SwitchOut> {
            val n = it.next
            consumer.execute(n?.let { JSAnimation(it) })
        }
    }

    @HostAccess.Export
    fun onEnd(consumer: Value) {
        anim.onEvent<AnimEvent.End> {
            consumer.execute(it.by.javaClass.simpleName)
        }
    }

    @HostAccess.Export
    fun onCompleted(consumer: Value) {
        anim.onEvent<AnimEvent.Completed> {
            consumer.execute()
        }
    }

}