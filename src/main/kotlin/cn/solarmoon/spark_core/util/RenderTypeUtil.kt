package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.client.SparkShaders
import com.google.common.collect.ImmutableList
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object RenderTypeUtil {

    @JvmStatic
    fun transparentRepair(location: ResourceLocation, blur: Boolean = false): RenderType = RenderType.create(
        "transparent_repair_entity", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER)
            .setTextureState(RenderStateShard.TextureStateShard(location, blur, true))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderType.NO_CULL)
            .setLightmapState(RenderType.LIGHTMAP)
            .setOverlayState(RenderType.OVERLAY)
            .setOutputState(RenderType.TRANSLUCENT_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(true)
    )

    @JvmStatic
    fun pureEffect(time: Float, strength: Float): RenderType = RenderType.create(
        "pure_effect",
        DefaultVertexFormat.POSITION_TEX,
        VertexFormat.Mode.QUADS,
        1536,
        true,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.ShaderStateShard {
                SparkShaders.DISTORT_SHADER.apply {
                    RenderSystem.setShaderTexture(0, Minecraft.getInstance().mainRenderTarget.colorTextureId)
                    safeGetUniform("Time").set(time)
                    safeGetUniform("Strength").set(strength)
                }
            })
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderType.NO_CULL)
            .setOutputState(RenderType.TRANSLUCENT_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(true)
    )



}