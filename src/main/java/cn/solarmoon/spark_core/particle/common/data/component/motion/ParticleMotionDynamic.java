package cn.solarmoon.spark_core.particle.common.data.component.motion;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 粒子动力学运动组件。
 * 控制粒子的线性加速度、线性阻力、旋转加速度和旋转阻力。
 * 对应 JSON key: minecraft:particle_motion_dynamic
 */
public class ParticleMotionDynamic implements IParticleComponentDefinition {

    private final String linearAccelerationX;
    private final String linearAccelerationY;
    private final String linearAccelerationZ;
    private final String linearDragCoefficient;
    private final String rotationAcceleration;
    private final String rotationDragCoefficient;

    public ParticleMotionDynamic(String linearAccelerationX, String linearAccelerationY, String linearAccelerationZ,
                                  String linearDragCoefficient, String rotationAcceleration,
                                  String rotationDragCoefficient) {
        this.linearAccelerationX = linearAccelerationX;
        this.linearAccelerationY = linearAccelerationY;
        this.linearAccelerationZ = linearAccelerationZ;
        this.linearDragCoefficient = linearDragCoefficient;
        this.rotationAcceleration = rotationAcceleration;
        this.rotationDragCoefficient = rotationDragCoefficient;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleMotionDynamic fromJson(JsonObject json) {
        JsonArray accel = json.getAsJsonArray("linear_acceleration");
        String ax = "0", ay = "0", az = "0";
        if (accel != null && accel.size() >= 3) {
            ax = accel.get(0).getAsString();
            ay = accel.get(1).getAsString();
            az = accel.get(2).getAsString();
        }
        String drag = getString(json, "linear_drag_coefficient", "0");
        String rotAccel = getString(json, "rotation_acceleration", "0");
        String rotDrag = getString(json, "rotation_drag_coefficient", "0");
        return new ParticleMotionDynamic(ax, ay, az, drag, rotAccel, rotDrag);
    }

    private static String getString(JsonObject json, String key, String def) {
        JsonElement el = json.get(key);
        return el != null ? el.getAsString() : def;
    }

    @Override
    public int order() {
        return 300;
    }

    // --- Getter ---

    public String getLinearAccelerationX() { return linearAccelerationX; }
    public String getLinearAccelerationY() { return linearAccelerationY; }
    public String getLinearAccelerationZ() { return linearAccelerationZ; }
    public String getLinearDragCoefficient() { return linearDragCoefficient; }
    public String getRotationAcceleration() { return rotationAcceleration; }
    public String getRotationDragCoefficient() { return rotationDragCoefficient; }
}
