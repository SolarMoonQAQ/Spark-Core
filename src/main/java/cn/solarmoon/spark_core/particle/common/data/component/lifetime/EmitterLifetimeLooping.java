package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 循环发射生命周期组件。<br>
 * 发射器在 active_time 秒内发射粒子，然后休眠 sleep_time 秒，循环往复。
 * 对应 JSON key: minecraft:emitter_lifetime_looping
 */
public class EmitterLifetimeLooping implements IEmitterComponentDefinition {

    private final float activeTime;
    private final float sleepTime;

    public EmitterLifetimeLooping(float activeTime, float sleepTime) {
        this.activeTime = activeTime;
        this.sleepTime = sleepTime;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterLifetimeLooping fromJson(JsonObject json) {
        float active = json.has("active_time") ? json.get("active_time").getAsFloat() : 10.0f;
        float sleep = json.has("sleep_time") ? json.get("sleep_time").getAsFloat() : 0.0f;
        return new EmitterLifetimeLooping(active, sleep);
    }

    @Override
    public int order() {
        return 500;
    }

    public float getActiveTime() {
        return activeTime;
    }

    public float getSleepTime() {
        return sleepTime;
    }
}
