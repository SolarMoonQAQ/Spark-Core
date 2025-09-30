package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.registry.client.SparkShaders
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.util.RenderTypeUtil
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer.Companion.ALL_VISUAL_EFFECTS
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
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

//    @SubscribeEvent
//    private fun render(event: RenderLevelStageEvent) {
//        val partialTicks = event.partialTick.gameTimeDeltaTicks
//        val camPos = event.camera.position
//        if (event.stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
//            SparkVisualEffects.TRAIL.apply {
//                meshes.forEach { mesh ->
//                    RenderSystem.enableDepthTest()
//                    RenderSystem.setShaderTexture(0, Minecraft.getInstance().mainRenderTarget.colorTextureId)
//                    RenderSystem.setShaderTexture(1, ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "textures/test.png"))
//                    RenderSystem.setShader { SparkShaders.STATIC_DISTORT }
//                    RenderSystem.enableBlend()
//                    RenderSystem.defaultBlendFunc()
//
//                    val tesselator = Tesselator.getInstance()
//                    val builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY)
//
//                    mesh.frame(partialTicks)
//                    renderMesh(mesh, camPos.toVector3f(), builder, partialTicks)
//
//                    builder.build()?.apply {
//                        BufferUploader.drawWithShader(this)
//                    }
//                    RenderSystem.disableBlend()
//                }
//            }
//        }
//    }

}