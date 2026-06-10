package cn.solarmoon.spark_core.particle.common.data.component.rate;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 瞬时发射速率组件。<br>
 * 发射器激活时一次性发出指定数量的粒子，然后结束。
 * 对应 JSON key: minecraft:emitter_rate_instant
 */
public class EmitterRateInstant implements IEmitterComponentDefinition {

    private final String numParticles;

    public EmitterRateInstant(String numParticles) {
        this.numParticles = numParticles;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterRateInstant fromJson(JsonObject json) {
        String num = json.has("num_particles") ? json.get("num_particles").getAsString() : "1";
        return new EmitterRateInstant(num);
    }

    @Override
    public int order() {
        return 0;
    }

    public String getNumParticles() {
        return numParticles;
    }
}
