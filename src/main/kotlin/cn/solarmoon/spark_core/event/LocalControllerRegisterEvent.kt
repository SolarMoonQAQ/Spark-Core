package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.local_control.LocalControlApplier
import cn.solarmoon.spark_core.local_control.LocalInputController
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class LocalControllerRegisterEvent: Event(), IModBusEvent {

    fun register(controller: LocalInputController) {
        LocalControlApplier.allControllers.add(controller)
    }

}