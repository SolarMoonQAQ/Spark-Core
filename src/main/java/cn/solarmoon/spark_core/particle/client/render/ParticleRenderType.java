package cn.solarmoon.spark_core.particle.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * 粒子系统专用 RenderType 工厂。
 * 继承 RenderType 以访问 protected 的 RenderStateShard 常量。
 */
public abstract class ParticleRenderType extends RenderType {

    private ParticleRenderType() {
        super("dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    /**
     * additive（叠加）混合模式 RenderType 的缓存。
     * 复用原版 Util.memoize 模式，避免高频渲染中每帧重复创建 CompositeState / RenderType 对象。
     */
    private static final Function<ResourceLocation, RenderType> ADDITIVE_PARTICLE_CACHE =
            Util.memoize(ParticleRenderType::createAdditiveParticle);

    /**
     * 获取 additive（叠加）混合模式的 RenderType（缓存）。
     * 使用 RENDERTYPE_ENTITY_TRANSLUCENT 着色器 + ADDITIVE 透明度。
     */
    public static RenderType additiveParticle(ResourceLocation texture) {
        return ADDITIVE_PARTICLE_CACHE.apply(texture);
    }

    /**
     * 实际构建 additive RenderType（由 memoize 缓存驱动，每个 texture 只调用一次）。
     */
    private static RenderType createAdditiveParticle(ResourceLocation texture) {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
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
