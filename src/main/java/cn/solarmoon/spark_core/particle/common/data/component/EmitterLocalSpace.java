package cn.solarmoon.spark_core.particle.common.data.component;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 发射器局部空间组件。<br>
 * 控制粒子是否继承发射器的位置、旋转和速度。
 * 对应 JSON key: minecraft:emitter_local_space
 */
public class EmitterLocalSpace implements IEmitterComponentDefinition {

    private final boolean position;
    private final boolean rotation;
    private final boolean velocity;

    public EmitterLocalSpace(boolean position, boolean rotation, boolean velocity) {
        this.position = position;
        this.rotation = rotation;
        this.velocity = velocity;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterLocalSpace fromJson(JsonObject json) {
        boolean pos = json.has("position") && json.get("position").getAsBoolean();
        boolean rot = json.has("rotation") && json.get("rotation").getAsBoolean();
        boolean vel = json.has("velocity") && json.get("velocity").getAsBoolean();
        return new EmitterLocalSpace(pos, rot, vel);
    }

    @Override
    public int order() {
        return 400;
    }

    public boolean isPosition() {
        return position;
    }

    public boolean isRotation() {
        return rotation;
    }

    public boolean isVelocity() {
        return velocity;
    }
}
