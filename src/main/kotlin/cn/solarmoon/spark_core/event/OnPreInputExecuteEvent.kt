package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.preinput.PreInputData
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent

abstract class OnPreInputExecuteEvent(
    val data: PreInputData
): Event() {

    class Pre(data: PreInputData): OnPreInputExecuteEvent(data), ICancellableEvent

    class Post(data: PreInputData): OnPreInputExecuteEvent(data)

}