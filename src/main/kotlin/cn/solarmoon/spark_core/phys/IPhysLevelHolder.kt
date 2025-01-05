package cn.solarmoon.spark_core.phys

import cn.solarmoon.spark_core.phys.thread.PhysLevel

interface IPhysLevelHolder {

    val allPhysLevel: LinkedHashMap<String, PhysLevel>

}