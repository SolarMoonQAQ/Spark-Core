package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.animation.anim.play.KeyframeRange
import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.execute
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import org.graalvm.polyglot.Value

@LuaClass("KeyframeRange")
interface LuaKeyframeRange {

    val self get() = this as KeyframeRange

    fun onEnter(consumer: LuaValueProxy) {
        self.onEnter {
            consumer.execute()
        }
    }

    fun onInside(consumer: LuaValueProxy) {
        self.onInside {
            consumer.execute(it.time)
        }
    }

    fun onExit(consumer: LuaValueProxy) {
        self.onExit {
            consumer.execute()
        }
    }

}