package cn.solarmoon.spark_core.particle.common.data.component.appearance;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 粒子光照外观组件。
 * 标记粒子受光照影响（无额外数据字段）。
 * 对应 JSON key: minecraft:particle_appearance_lighting
 */
public class ParticleAppearanceLighting implements IParticleComponentDefinition {

    /**
     * 从 JSON 对象反序列化。此组件无需额外数据。
     */
    public static ParticleAppearanceLighting fromJson(JsonObject json) {
        return new ParticleAppearanceLighting();
    }

    @Override
    public int order() {
        return 200;
    }
}
