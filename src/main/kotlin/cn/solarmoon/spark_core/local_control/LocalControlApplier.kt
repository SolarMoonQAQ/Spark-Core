package cn.solarmoon.spark_core.local_control

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.LocalControllerRegisterEvent
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.Input
import net.minecraft.client.player.LocalPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.network.PacketDistributor

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
    }

    @SubscribeEvent
    private fun sendPackage(event: ClientTickEvent.Post) {
        allControllers.forEach {
            while (it.packages.isNotEmpty()) {
                PacketDistributor.sendToServer(it.packages.removeLast())
            }
        }
    }

}