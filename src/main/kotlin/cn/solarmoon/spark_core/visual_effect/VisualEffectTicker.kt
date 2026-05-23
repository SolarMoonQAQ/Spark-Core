package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer.Companion.ALL_VISUAL_EFFECTS
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

object VisualEffectTicker {

    @SubscribeEvent
    private fun tick(event: ClientTickEvent.Pre) {
        ALL_VISUAL_EFFECTS.forEach { it.tick() }
    }

    @SubscribeEvent
    private fun physTick(event: PhysicsLevelTickEvent.Pre) {
        ALL_VISUAL_EFFECTS.forEach { it.physTick(event.level) }
    }

    @SubscribeEvent
    private fun onRenderStage(event: RenderLevelStageEvent) {
        val stage = event.stage
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        val partialTicks = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(true)
        for (renderer in ALL_VISUAL_EFFECTS) {
            if (renderer.getRenderStage() == stage) {
                renderer.render(event, bufferSource, partialTicks)
            }
        }
    }

}