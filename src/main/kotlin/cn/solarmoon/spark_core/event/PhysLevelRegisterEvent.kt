package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.phys.thread.PhysLevel
import net.minecraft.world.level.Level
import net.neoforged.bus.api.Event

class PhysLevelRegisterEvent(
    val level: Level,
    private val map: LinkedHashMap<String, PhysLevel>
): Event() {

    fun register(pl: PhysLevel) {
        if (map.get(pl.id) != null) throw ExceptionInInitializerError("已经存在名为 ${pl.id} 的物理线程，请换一个名字防止冲突。")
        else map[pl.id] = pl
    }

}