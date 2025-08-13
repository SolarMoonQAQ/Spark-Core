package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.preinput.PreInput
import cn.solarmoon.spark_core.preinput.PreInputData
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent

abstract class OnPreInputExecuteEvent(
    val preInput: PreInput,
    val data: PreInputData
): Event() {

    class Pre(preInput: PreInput, data: PreInputData): OnPreInputExecuteEvent(preInput, data), ICancellableEvent

    class Post(preInput: PreInput, data: PreInputData): OnPreInputExecuteEvent(preInput, data)

}