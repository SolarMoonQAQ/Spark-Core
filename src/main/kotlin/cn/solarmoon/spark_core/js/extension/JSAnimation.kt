package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.util.PPhase
import org.graalvm.polyglot.HostAccess
import org.mozilla.javascript.Function

interface JSAnimation {

    val anim get() = this as AnimInstance

    val js get() = anim.holder.animLevel.jsEngine

    @HostAccess.Export
    fun getProgress() = anim.getProgress(1f)

    @HostAccess.Export
    fun onSwitchIn(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchIn> {
            val p = it.previous
            consumer.call(js, p)
        }
    }

    @HostAccess.Export
    fun onSwitchOut(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchOut> {
            val n = it.next
            consumer.call(js, n)
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