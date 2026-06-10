package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 单次发射生命周期组件。<br>
 * 发射器激活后运行指定的 active_time 秒，然后结束。
 * 对应 JSON key: minecraft:emitter_lifetime_once
 */
public class EmitterLifetimeOnce implements IEmitterComponentDefinition {

    private final float activeTime;

    public EmitterLifetimeOnce(float activeTime) {
        this.activeTime = activeTime;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterLifetimeOnce fromJson(JsonObject json) {
        float time = json.has("active_time") ? json.get("active_time").getAsFloat() : 10.0f;
        return new EmitterLifetimeOnce(time);
    }

    @Override
    public int order() {
        return 500;
    }

    public float getActiveTime() {
        return activeTime;
    }
}
