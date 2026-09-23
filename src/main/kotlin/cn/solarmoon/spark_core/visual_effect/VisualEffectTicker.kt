package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer.Companion.ALL_VISUAL_EFFECTS
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

object VisualEffectTicker {

    /**
     * 客户端渲染特效的主线程 tick。
     * <p>
     * 挂在 [ClientTickEvent.Post]（`Minecraft#tick()` 尾）而非 `Pre`（`Minecraft#tick()` 首）：
     * NeoForge 的 `ClientTickEvent.Pre` 在整个客户端 tick 的第一行触发，早于该 tick 内的
     * `LevelTickEvent`（即 `level.tick()`），而 `Post` 在其之后。粒子发射器实例由
     * [cn.solarmoon.spark_core.particle.common.ParticleEmitterManager.add] 入队、
     * 在本方法内消费——若在 `Pre` 驱动，level tick 期间（如武器开火）请求的炮口火光
     * 要等到下一个 tick 才首次发射粒子，视觉上比曳光弹晚约 1 tick（50ms）。
     * 放在 `Post` 可让同一 tick 内的请求当帧进入发射与渲染。
     */
    @SubscribeEvent
    private fun tick(event: ClientTickEvent.Post) {
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