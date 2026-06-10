package cn.solarmoon.spark_core.particle.common.data.component.shape;

import cn.solarmoon.spark_core.particle.common.data.component.EmitterShapeAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 球体发射器形状组件。<br>
 * 粒子从球体表面或内部发射。
 * 对应 JSON key: minecraft:emitter_shape_sphere
 */
public class EmitterShapeSphere extends EmitterShapeAdapter {

    private final String radius;
    private final boolean surfaceOnly;
    private final boolean directionInwards;

    public EmitterShapeSphere(String[] offset, String[] direction,
                               String radius, boolean surfaceOnly, boolean directionInwards) {
        super(offset, direction);
        this.radius = radius;
        this.surfaceOnly = surfaceOnly;
        this.directionInwards = directionInwards;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterShapeSphere fromJson(JsonObject json) {
        String[] offset = parseStringArray(json, "offset", "0", "0", "0");
        String[] dir = parseStringArray(json, "direction", "0", "0", "1");
        String radius = json.has("radius") ? json.get("radius").getAsString() : "1";
        boolean surface = json.has("surface_only") && json.get("surface_only").getAsBoolean();
        boolean inward = json.has("direction_inwards") && json.get("direction_inwards").getAsBoolean();
        return new EmitterShapeSphere(offset, dir, radius, surface, inward);
    }

    public String getRadius() {
        return radius;
    }

    public boolean isSurfaceOnly() {
        return surfaceOnly;
    }

    public boolean isDirectionInwards() {
        return directionInwards;
    }

    /**
     * 从 JSON 对象中读取字符串数组字段。
     */
    private static String[] parseStringArray(JsonObject json, String key, String defX, String defY, String defZ) {
        JsonArray arr = json.getAsJsonArray(key);
        if (arr != null && arr.size() >= 3) {
            return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString(), arr.get(2).getAsString()};
        }
        return new String[]{defX, defY, defZ};
    }
}
