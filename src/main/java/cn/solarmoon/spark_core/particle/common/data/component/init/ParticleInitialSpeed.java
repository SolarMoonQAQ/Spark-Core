package cn.solarmoon.spark_core.particle.common.data.component.init;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * 粒子初始速度组件。对应 JSON key: minecraft:particle_initial_speed
 * <p>
 * 支持基岩版三种格式：
 * <ol>
 *   <li>裸数字：{@code 5.0}</li>
 *   <li>裸 Molang 字符串：{@code "v.particle_random_1 * 5"}</li>
 *   <li>对象包装：{@code {"speed": 5.0}}</li>
 * </ol>
 */
public class ParticleInitialSpeed implements IParticleComponentDefinition {

    private final String speed;

    public ParticleInitialSpeed(String speed) {
        this.speed = speed;
    }

    /**
     * 从原始 JsonElement 反序列化。支持裸值（数字/字符串）和对象两种格式。
     */
    public static ParticleInitialSpeed fromJson(JsonElement value) {
        String speed;
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            speed = obj.has("speed") ? obj.get("speed").getAsString() : "0";
        } else if (value.isJsonPrimitive()) {
            JsonPrimitive prim = value.getAsJsonPrimitive();
            speed = prim.isString() ? prim.getAsString() : prim.getAsString(); // 数字取其字符串表示
        } else {
            speed = "0";
        }
        return new ParticleInitialSpeed(speed);
    }

    @Override
    public int order() {
        return -500;
    }

    public String getSpeed() { return speed; }
}
