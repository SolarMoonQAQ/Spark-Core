package cn.solarmoon.spark_core.particle.common.data.component.init;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 粒子初始旋转组件。
 * 控制粒子的初始旋转角度和旋转速率。
 * 对应 JSON key: minecraft:particle_initial_spin
 */
public class ParticleInitialSpin implements IParticleComponentDefinition {

    private final String rotation;
    private final String rotationRate;

    public ParticleInitialSpin(String rotation, String rotationRate) {
        this.rotation = rotation;
        this.rotationRate = rotationRate;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleInitialSpin fromJson(JsonObject json) {
        String rot = json.has("rotation") ? json.get("rotation").getAsString() : "0";
        String rate = json.has("rotation_rate") ? json.get("rotation_rate").getAsString() : "0";
        return new ParticleInitialSpin(rot, rate);
    }

    @Override
    public int order() {
        return -490;
    }

    public String getRotation() { return rotation; }
    public String getRotationRate() { return rotationRate; }
}
