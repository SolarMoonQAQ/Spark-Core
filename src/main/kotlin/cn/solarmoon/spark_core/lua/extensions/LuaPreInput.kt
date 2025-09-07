package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.execute
import cn.solarmoon.spark_core.preinput.PreInput
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.world.entity.Entity
import org.mozilla.javascript.Function

@LuaClass("PreInput")
interface LuaPreInput {

    val preInput get() = this as PreInput

    fun execute() {
        preInput.execute()
    }

    fun execute(consumer: LuaValueProxy) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.execute {
                consumer.execute()
            }
        }
    }

    fun executeIfPresent(vararg id: String) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeIfPresent(*id)
        }
    }

    fun executeIfPresent(vararg id: String, consumer: LuaValueProxy) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeIfPresent(*id) {
                consumer.execute()
            }
        }
    }

    fun executeExcept(vararg id: String) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeExcept(*id)
        }
    }

    fun executeExcept(vararg id: String, consumer: LuaValueProxy) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeExcept(*id) {
                consumer.execute()
            }
        }
    }

}