package cn.solarmoon.spark_core.particle.common.data.component.rate;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 稳态发射速率组件。<br>
 * 发射器以固定速率持续发射粒子，直到达到最大粒子数。
 * 对应 JSON key: minecraft:emitter_rate_steady
 */
public class EmitterRateSteady implements IEmitterComponentDefinition {

    private final String spawnRate;
    private final String maxParticles;

    public EmitterRateSteady(String spawnRate, String maxParticles) {
        this.spawnRate = spawnRate;
        this.maxParticles = maxParticles;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterRateSteady fromJson(JsonObject json) {
        String rate = json.has("spawn_rate") ? json.get("spawn_rate").getAsString() : "1";
        String max = json.has("max_particles") ? json.get("max_particles").getAsString() : "50";
        return new EmitterRateSteady(rate, max);
    }

    @Override
    public int order() {
        return 0;
    }

    public String getSpawnRate() {
        return spawnRate;
    }

    public String getMaxParticles() {
        return maxParticles;
    }
}
