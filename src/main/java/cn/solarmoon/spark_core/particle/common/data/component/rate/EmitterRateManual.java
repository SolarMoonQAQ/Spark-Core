package cn.solarmoon.spark_core.particle.common.data.component.rate;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 手动发射速率组件。<br>
 * 发射器不自动发射粒子，由外部事件触发发射。
 * 对应 JSON key: minecraft:emitter_rate_manual
 */
public class EmitterRateManual implements IEmitterComponentDefinition {

    private final String maxParticles;

    public EmitterRateManual(String maxParticles) {
        this.maxParticles = maxParticles;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterRateManual fromJson(JsonObject json) {
        String max = json.has("max_particles") ? json.get("max_particles").getAsString() : "50";
        return new EmitterRateManual(max);
    }

    @Override
    public int order() {
        return 0;
    }

    public String getMaxParticles() {
        return maxParticles;
    }
}
