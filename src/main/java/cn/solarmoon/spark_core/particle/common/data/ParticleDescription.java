package cn.solarmoon.spark_core.particle.common.data;

import net.minecraft.resources.ResourceLocation;

/**
 * 粒子描述信息，对应 JSON 中的 description 块。
 */
public class ParticleDescription {
    private final ResourceLocation identifier;
    private final String material;
    private final ResourceLocation texture;

    public ParticleDescription(ResourceLocation identifier, String material, ResourceLocation texture) {
        this.identifier = identifier;
        this.material = material;
        this.texture = texture;
    }

    public ResourceLocation getIdentifier() { return identifier; }
    public String getMaterial() { return material; }
    public ResourceLocation getTexture() { return texture; }

    /** 材质枚举映射 */
    public enum Material {
        PARTICLES_BLEND("particles_blend"),
        PARTICLES_OPAQUE("particles_opaque"),
        PARTICLES_ALPHA("particles_alpha"),
        PARTICLES_ADD("particles_add");

        private final String key;
        Material(String key) { this.key = key; }
        public String getKey() { return key; }

        public static Material fromKey(String key) {
            for (Material m : values()) {
                if (m.key.equals(key)) return m;
            }
            return PARTICLES_BLEND;
        }
    }
}
