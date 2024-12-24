package cn.solarmoon.spark_core.visual_effect.common.shadow

import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

class ShadowRenderer: VisualEffectRenderer() {

    private val allShadows = ConcurrentLinkedQueue<Shadow>()

    fun add(shadow: Shadow) {
        allShadows.add(shadow)
    }

    fun addToClient(entityId: Int, maxLifeTime: Int = 20, color: Color = Color.GRAY) {
        PacketDistributor.sendToAllPlayers(ShadowPayload(entityId, maxLifeTime, color.rgb))
    }

    override fun tick() {
        val iterator = allShadows.iterator()
        while (iterator.hasNext()) {
            val shadow = iterator.next()
            shadow.tick()
            if (shadow.isRemoved) {
                iterator.remove()
            }
        }
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        val level = mc.level ?: return
        allShadows.forEach {
            it.render(level, poseStack, bufferSource, partialTicks)
        }
    }

}