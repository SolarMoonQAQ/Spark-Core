package cn.solarmoon.spark_core.particle.common.data.component.shape;

import cn.solarmoon.spark_core.particle.common.data.component.EmitterShapeAdapter;
import com.google.gson.JsonObject;

/**
 * 盒体发射器形状组件。<br>
 * 粒子从长方体表面或内部发射。
 * 对应 JSON key: minecraft:emitter_shape_box
 */
public class EmitterShapeBox extends EmitterShapeAdapter {

    private final String[] halfDimensions;
    private final boolean surfaceOnly;

    public EmitterShapeBox(String[] offset, String[] direction,
                            String[] halfDimensions, boolean surfaceOnly) {
        super(offset, direction);
        this.halfDimensions = halfDimensions;
        this.surfaceOnly = surfaceOnly;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterShapeBox fromJson(JsonObject json) {
        String[] offset = readVec3(json, "offset", "0", "0", "0");
        String[] dir = readDirection(json, "0", "0", "1");
        String[] half = readVec3(json, "half_dimensions", "1", "1", "1");
        boolean surface = json.has("surface_only") && json.get("surface_only").getAsBoolean();
        return new EmitterShapeBox(offset, dir, half, surface);
    }

    public String[] getHalfDimensions() {
        return halfDimensions;
    }

    public boolean isSurfaceOnly() {
        return surfaceOnly;
    }
}
