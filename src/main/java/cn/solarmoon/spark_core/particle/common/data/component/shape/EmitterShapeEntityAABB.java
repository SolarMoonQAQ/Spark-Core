package cn.solarmoon.spark_core.particle.common.data.component.shape;

import cn.solarmoon.spark_core.particle.common.data.component.EmitterShapeAdapter;
import com.google.gson.JsonObject;

/**
 * 实体包围盒发射器形状组件。<br>
 * 粒子从附着实体的 AABB 包围盒内发射。
 * 对应 JSON key: minecraft:emitter_shape_entity_aabb
 */
public class EmitterShapeEntityAABB extends EmitterShapeAdapter {

    public EmitterShapeEntityAABB() {
        super(new String[]{"0", "0", "0"}, new String[]{"0", "0", "1"});
    }

    /**
     * 从 JSON 对象反序列化。<br>
     * 该组件没有额外字段，直接返回默认实例。
     */
    public static EmitterShapeEntityAABB fromJson(JsonObject json) {
        return new EmitterShapeEntityAABB();
    }
}
