package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.phys.thread.PhysLevel
import net.neoforged.bus.api.Event

open class PhysLevelTickEvent(val level: PhysLevel): Event() {



}