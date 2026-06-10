package cn.solarmoon.spark_core.particle.common.data.component.init;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 粒子初始速度组件。
 * 对应 JSON key: minecraft:particle_initial_speed
 */
public class ParticleInitialSpeed implements IParticleComponentDefinition {

    private final String speed;

    public ParticleInitialSpeed(String speed) {
        this.speed = speed;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleInitialSpeed fromJson(JsonObject json) {
        String speed = json.has("speed") ? json.get("speed").getAsString() : "0";
        return new ParticleInitialSpeed(speed);
    }

    @Override
    public int order() {
        return -500;
    }

    public String getSpeed() { return speed; }
}
