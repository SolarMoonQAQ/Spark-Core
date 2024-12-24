package cn.solarmoon.spark_core.util

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
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
            .setOutputState(RenderType.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(true)
    )

}