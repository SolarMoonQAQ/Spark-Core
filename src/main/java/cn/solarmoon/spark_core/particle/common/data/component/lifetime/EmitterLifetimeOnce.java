package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 单次发射生命周期组件。
 * 发射器激活后运行指定的 active_time 秒，然后结束。
 * 对应 JSON key: minecraft:emitter_lifetime_once
 * <p>
 * 对标 SBM：{@code active_time} 存储为 Molang 表达式字符串（可为数字如 "10" 或表达式）。
 */
public class EmitterLifetimeOnce implements IEmitterComponentDefinition {

    private final String activeTime;

    public EmitterLifetimeOnce(String activeTime) {
        this.activeTime = activeTime;
    }

    /**
     * 从 JSON 对象反序列化。active_time 支持数字或 Molang 表达式字符串。
     */
    public static EmitterLifetimeOnce fromJson(JsonObject json) {
        String time;
        if (json.has("active_time")) {
            time = json.get("active_time").getAsString();
        } else {
            time = "10";
        }
        return new EmitterLifetimeOnce(time);
    }

    @Override
    public int order() {
        return 500;
    }

    /** 返回 active_time 的 Molang 表达式字符串（可为纯数字） */
    public String getActiveTime() {
        return activeTime;
    }
}
