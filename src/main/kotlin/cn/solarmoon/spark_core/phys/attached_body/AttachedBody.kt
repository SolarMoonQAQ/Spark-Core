package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.phys.thread.PhysLevel
import org.ode4j.ode.DBody

interface AttachedBody {

    val name: String

    val physLevel: PhysLevel

    val body: DBody

    fun enable() {
        body.enable()
    }

    fun disable() {
        body.disable()
    }

    fun destroy() {
        body.destroy()
    }

}