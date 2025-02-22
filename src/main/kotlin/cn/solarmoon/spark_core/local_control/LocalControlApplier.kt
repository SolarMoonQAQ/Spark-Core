package cn.solarmoon.spark_core.local_control

import cn.solarmoon.spark_core.client.gui.screen.AnimationDebugScreen
import cn.solarmoon.spark_core.registry.common.KeyBindings
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent

object LocalControlApplier {

    val allControllers = mutableListOf<LocalInputController>()

    @SubscribeEvent
    private fun tick(event: MovementInputUpdateEvent) {
        val player = event.entity as LocalPlayer
        val input = event.input
        allControllers.forEach {
            it.lateInit()
            it.tick(player, input)
            it.keyTick()
            it.updateMovement(player, event)
        }
    }

    @SubscribeEvent
    private fun interact(event: InputEvent.InteractionKeyMappingTriggered) {
        val player = Minecraft.getInstance().player ?: return
        allControllers.forEach {
            it.onInteract(player, event)
            if (event.isCanceled) return@forEach
        }

        // 检查是否打开调试界面
        if (event.keyMapping == KeyBindings.OPEN_ANIMATION_DEBUG) {
            Minecraft.getInstance().setScreen(AnimationDebugScreen())
        }
    }

}