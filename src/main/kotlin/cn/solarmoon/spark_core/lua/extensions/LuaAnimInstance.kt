package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.js.toVector2d
import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.execute
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import org.mozilla.javascript.NativeArray

@LuaClass("AnimInstance")
interface LuaAnimInstance {

    val anim get() = this as AnimInstance

    fun getProgress() = anim.getProgress(1f)

    fun onSwitchIn(consumer: LuaValueProxy) {
        anim.onEvent<AnimEvent.SwitchIn> {
            val p = it.previous
            consumer.execute(p)
        }
    }

    fun onSwitchOut(consumer: LuaValueProxy) {
        anim.onEvent<AnimEvent.SwitchOut> {
            val n = it.originNextAnim
            consumer.execute(n)
        }
    }

    fun onEnd(consumer: LuaValueProxy) {
        anim.onEvent<AnimEvent.End> {
            consumer.execute()
        }
    }

    fun onCompleted(consumer: LuaValueProxy) {
        anim.onEvent<AnimEvent.Completed> {
            consumer.execute()
        }
    }

    fun onStart(consumer: LuaValueProxy) {
        anim.onEvent<AnimEvent.SwitchIn> {
            consumer.execute(it.previous)
        }
    }

    fun setShouldTurnBody(bool: Boolean) {
        anim.shouldTurnBody = bool
    }

    fun registerKeyframeRangeEnd(id: String, end: Double) = anim.registerKeyframeRange(id, 0.0, end)

    fun registerKeyframeRangeStart(id: String, start: Double) = anim.registerKeyframeRange(id, start, anim.maxLength)

    fun registerKeyframeRanges(id: String, ranges: NativeArray, provider: LuaValueProxy) = anim.registerKeyframeRanges(id, *ranges.map { (it as NativeArray).toVector2d() }.toTypedArray()) {
        provider.execute(this, it)
    }

}