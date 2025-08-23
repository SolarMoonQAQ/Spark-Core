package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.preinput.PreInput
import cn.solarmoon.spark_core.preinput.PreInputData
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent

abstract class PreInputEvent(
    val preInput: PreInput
): Event() {

    abstract class Execute(preInput: PreInput, val data: PreInputData): PreInputEvent(preInput) {
        class Pre(preInput: PreInput, data: PreInputData): Execute(preInput, data), ICancellableEvent

        class Post(preInput: PreInput, data: PreInputData): Execute(preInput,data)
    }

    class Lock(preInput: PreInput): PreInputEvent(preInput), ICancellableEvent

    class Unlock(preInput: PreInput): PreInputEvent(preInput), ICancellableEvent

}