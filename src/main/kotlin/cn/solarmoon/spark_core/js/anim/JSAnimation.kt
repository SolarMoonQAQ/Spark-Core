package cn.solarmoon.spark_core.js.anim

import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.util.PPhase
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.mozilla.javascript.Function

class JSAnimation(
    val js: SparkJS,
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
    fun onSwitchIn(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchIn> {
            val p = it.previous
            consumer.call(js, p?.let { JSAnimation(js, it) })
        }
    }

    @HostAccess.Export
    fun onSwitchOut(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchOut> {
            val n = it.next
            consumer.call(js, n?.let { JSAnimation(js, it) })
        }
    }

    @HostAccess.Export
    fun onEnd(consumer: Function) {
        anim.holder.animLevel.submitImmediateTask(PPhase.POST) {
            anim.onEvent<AnimEvent.End> {
                consumer.call(js, it.by.javaClass.simpleName)
            }
        }
    }

    @HostAccess.Export
    fun onCompleted(consumer: Function) {
        anim.holder.animLevel.submitImmediateTask(PPhase.POST) {
            anim.onEvent<AnimEvent.Completed> {
                consumer.call(js)
            }
        }
    }

}