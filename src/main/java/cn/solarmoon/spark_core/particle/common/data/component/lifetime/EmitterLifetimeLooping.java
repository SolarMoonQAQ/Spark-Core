package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 循环发射生命周期组件。
 * 发射器在 active_time 秒内发射粒子，然后休眠 sleep_time 秒，循环往复。
 * 对应 JSON key: minecraft:emitter_lifetime_looping
 * <p>
 * 对标 SBM：{@code active_time} / {@code sleep_time} 存储为 Molang 表达式字符串。
 */
public class EmitterLifetimeLooping implements IEmitterComponentDefinition {

    private final String activeTime;
    private final String sleepTime;

    public EmitterLifetimeLooping(String activeTime, String sleepTime) {
        this.activeTime = activeTime;
        this.sleepTime = sleepTime;
    }

    /**
     * 从 JSON 对象反序列化。active_time / sleep_time 支持数字或 Molang 表达式。
     */
    public static EmitterLifetimeLooping fromJson(JsonObject json) {
        String active = json.has("active_time") ? json.get("active_time").getAsString() : "10";
        String sleep = json.has("sleep_time") ? json.get("sleep_time").getAsString() : "0";
        return new EmitterLifetimeLooping(active, sleep);
    }

    @Override
    public int order() {
        return 500;
    }

    public String getActiveTime() {
        return activeTime;
    }

    public String getSleepTime() {
        return sleepTime;
    }
}
