package cn.solarmoon.spark_core.phys.attached_body

import org.ode4j.ode.DBody

interface AttachedBody {

    val name: String

    val body: DBody

    fun enable() {
        body.enable()
    }

    fun disable() {
        body.disable()
    }

}