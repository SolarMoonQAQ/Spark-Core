package cn.solarmoon.spark_core.event

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import org.ode4j.ode.DBody

/**
 * 在[DBody]被创建到物理世界时注入，可立刻destroy以阻止该body创建
 */
class OnBodyCreateEvent(
    val body: DBody
): Event() {



}