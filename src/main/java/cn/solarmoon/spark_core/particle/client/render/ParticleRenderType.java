package cn.solarmoon.spark_core.particle.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 粒子系统专用 RenderType 工厂。
 * 继承 RenderType 以访问 protected 的 RenderStateShard 常量。
 */
public abstract class ParticleRenderType extends RenderType {

    private ParticleRenderType() {
        super("dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    /**
     * 创建 additive（叠加）混合模式的 RenderType。
     * 使用 RENDERTYPE_ENTITY_TRANSLUCENT 着色器 + ADDITIVE 透明度。
     */
    public static RenderType additiveParticle(ResourceLocation texture) {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(true);
        return create(
                "particle_additive",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true, true,
                state
        );
    }

    /**
     * 根据材质类型（material）选择对应的 RenderType。
     */
    public static RenderType fromMaterial(String material, ResourceLocation texture) {
        return switch (material) {
            case "particles_opaque", "particles_alpha" -> RenderType.entityCutout(texture);
            case "particles_add" -> additiveParticle(texture);
            default -> RenderType.entityTranslucent(texture);
        };
    }
}
