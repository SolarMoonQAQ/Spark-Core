package cn.solarmoon.spark_core.particle.common.data.component.shape;

import cn.solarmoon.spark_core.particle.common.data.component.EmitterShapeAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 点状发射器形状组件。<br>
 * 粒子从单个点发射。
 * 对应 JSON key: minecraft:emitter_shape_point
 */
public class EmitterShapePoint extends EmitterShapeAdapter {

    public EmitterShapePoint(String[] offset, String[] direction) {
        super(offset, direction);
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterShapePoint fromJson(JsonObject json) {
        String[] offset = parseStringArray(json, "offset", "0", "0", "0");
        String[] direction = parseStringArray(json, "direction", "0", "0", "1");
        return new EmitterShapePoint(offset, direction);
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
