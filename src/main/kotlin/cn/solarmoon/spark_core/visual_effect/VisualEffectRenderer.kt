package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

abstract class VisualEffectRenderer {

    init {
        ALL_VISUAL_EFFECTS.add(this)
    }

    abstract fun tick()

    abstract fun physTick(physLevel: PhysicsLevel)

    /**
     * 指定此渲染器希望在哪个渲染阶段执行。
     * 返回 null 则不参与 RenderLevelStageEvent 渲染调度。
     */
    open fun getRenderStage(): RenderLevelStageEvent.Stage? = null

    /**
     * 在指定的渲染阶段进行渲染。
     * 相比旧的 render(mc, camPos, ...)，此方法可从 event 中获取 Camera、Frustum、modelViewMatrix 等完整渲染上下文。
     * @param event     NeoForge 渲染阶段事件，包含相机、视锥体、矩阵栈等
     * @param bufferSource 通过 Minecraft.renderBuffers().bufferSource() 获取的缓冲区源
     */
    open fun render(event: RenderLevelStageEvent, bufferSource: MultiBufferSource) {}

    /**
     * 仅在不使用 RenderLevelStageEvent 时使用此方法。
     * 取而代之继承 [CustomDebugRenderer] 即可在 DebugRenderer 阶段渲染，而非依赖本类的旧路径。
     */
    @Deprecated("如需 DebugRenderer 阶段渲染，改为继承 CustomDebugRenderer")
    open fun render(mc: Minecraft, camPos: Vec3, poseStack: PoseStack, bufferSource: MultiBufferSource, partialTicks: Float) {}

    companion object {
        @JvmStatic
        val ALL_VISUAL_EFFECTS = mutableListOf<VisualEffectRenderer>()
    }

}