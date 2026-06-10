package cn.solarmoon.spark_core.particle.common.data.component.motion;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * 粒子参数化运动组件。
 * 通过随时间变化的 Molang 表达式控制粒子的相对位置、方向和旋转。
 * 对应 JSON key: minecraft:particle_motion_parametric
 */
public class ParticleMotionParametric implements IParticleComponentDefinition {

    private final String[] relativePosition;
    private final String[] direction;
    private final String rotation;

    public ParticleMotionParametric(String[] relativePosition, String[] direction, String rotation) {
        this.relativePosition = relativePosition;
        this.direction = direction;
        this.rotation = rotation;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleMotionParametric fromJson(JsonObject json) {
        String[] relPos = readStringArray(json, "relative_position", new String[]{"0", "0", "0"});
        String[] dir = readStringArray(json, "direction", new String[]{"0", "0", "0"});
        String rot = json.has("rotation") ? json.get("rotation").getAsString() : "0";
        return new ParticleMotionParametric(relPos, dir, rot);
    }

    private static String[] readStringArray(JsonObject json, String key, String[] def) {
        JsonArray arr = json.getAsJsonArray(key);
        if (arr == null || arr.size() < 3) return def;
        return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString(), arr.get(2).getAsString()};
    }

    @Override
    public int order() {
        return 300;
    }

    // --- Getter ---

    public String[] getRelativePosition() { return relativePosition; }
    public String[] getDirection() { return direction; }
    public String getRotation() { return rotation; }

    @Override
    public String toString() {
        return "ParticleMotionParametric{" +
                "relativePosition=" + Arrays.toString(relativePosition) +
                ", direction=" + Arrays.toString(direction) +
                ", rotation='" + rotation + '\'' +
                '}';
    }
}
