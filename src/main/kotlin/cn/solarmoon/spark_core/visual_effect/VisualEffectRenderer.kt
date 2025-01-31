package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3

abstract class VisualEffectRenderer {

    init {
        ALL_VISUAL_EFFECTS.add(this)
    }

    abstract fun tick()

    abstract fun physTick(physLevel: PhysicsLevel)

    abstract fun render(mc: Minecraft, camPos: Vec3, poseStack: PoseStack, bufferSource: MultiBufferSource, partialTicks: Float)

    companion object {
        @JvmStatic
        val ALL_VISUAL_EFFECTS = mutableListOf<VisualEffectRenderer>()
    }

}