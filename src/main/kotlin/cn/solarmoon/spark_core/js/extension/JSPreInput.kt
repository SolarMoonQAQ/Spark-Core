package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.preinput.PreInput
import net.minecraft.world.entity.Entity
import org.mozilla.javascript.Function

interface JSPreInput {

    val preInput get() = this as PreInput

    fun execute() {
        preInput.execute()
    }

    fun execute(consumer: Function) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.execute {
                consumer.call(holder.level().jsEngine)
            }
        }
    }

    fun executeIfPresent(vararg id: String) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeIfPresent(*id)
        }
    }

    fun executeIfPresent(vararg id: String, consumer: Function) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeIfPresent(*id) {
                consumer.call(holder.level().jsEngine)
            }
        }
    }

    fun executeExcept(vararg id: String) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeExcept(*id)
        }
    }

    fun executeExcept(vararg id: String, consumer: Function) {
        val holder = preInput.holder
        if (holder is Entity) {
            preInput.executeExcept(*id) {
                consumer.call(holder.level().jsEngine)
            }
        }
    }

}