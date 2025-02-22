package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.client.gui.screen.AnimationDebugScreen
import cn.solarmoon.spark_core.local_control.LocalControlApplier
import cn.solarmoon.spark_core.local_control.LocalInputController
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent
import net.minecraft.client.Minecraft

class LocalControllerRegisterEvent: Event(), IModBusEvent {

    fun register(controller: LocalInputController) {
        LocalControlApplier.allControllers.add(controller)

        // 打开调试界面
        if (controller.shouldOpenDebugScreen()) {
            Minecraft.getInstance().setScreen(AnimationDebugScreen())
        }
    }

}