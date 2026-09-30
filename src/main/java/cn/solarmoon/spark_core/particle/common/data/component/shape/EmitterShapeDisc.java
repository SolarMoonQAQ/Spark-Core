package cn.solarmoon.spark_core.particle.common.data.component.shape;

import cn.solarmoon.spark_core.particle.common.data.component.EmitterShapeAdapter;
import com.google.gson.JsonObject;

/**
 * 圆盘发射器形状组件。<br>
 * 粒子从圆盘表面或内部发射。
 * 对应 JSON key: minecraft:emitter_shape_disc
 */
public class EmitterShapeDisc extends EmitterShapeAdapter {

    private final String radius;
    private final boolean surfaceOnly;
    private final boolean directionInwards;
    private final String[] planeNormal;

    public EmitterShapeDisc(String[] offset, String[] direction,
                             String radius, boolean surfaceOnly, boolean directionInwards,
                             String[] planeNormal) {
        super(offset, direction);
        this.radius = radius;
        this.surfaceOnly = surfaceOnly;
        this.directionInwards = directionInwards;
        this.planeNormal = planeNormal;
    }

    /**
     * 从 JSON 对象反序列化。
     * {@code direction} 既接受三元素向量，也接受 {@code "inwards"} / {@code "outwards"} 字符串。
     */
    public static EmitterShapeDisc fromJson(JsonObject json) {
        String[] offset = readVec3(json, "offset", "0", "0", "0");
        String[] dir = readDirection(json, "0", "0", "1");
        String radius = json.has("radius") ? json.get("radius").getAsString() : "1";
        boolean surface = json.has("surface_only") && json.get("surface_only").getAsBoolean();
        boolean inward = readDirectionInwards(json);
        String[] normal = readVec3(json, "plane_normal", "0", "0", "1");
        return new EmitterShapeDisc(offset, dir, radius, surface, inward, normal);
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

    public String[] getPlaneNormal() {
        return planeNormal;
    }
}
