package cn.solarmoon.spark_core.particle.common.data.component.init;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 粒子初始化组件。
 * 定义粒子生成时和每帧更新的 Molang 表达式。
 * 对应 JSON key: minecraft:particle_initialization
 */
public class ParticleInitialization implements IParticleComponentDefinition {

    private final String perRenderExpression;
    private final String perUpdateExpression;

    public ParticleInitialization(String perRenderExpression, String perUpdateExpression) {
        this.perRenderExpression = perRenderExpression;
        this.perUpdateExpression = perUpdateExpression;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleInitialization fromJson(JsonObject json) {
        String perRender = json.has("per_render_expression") ? json.get("per_render_expression").getAsString() : "";
        String perUpdate = json.has("per_update_expression") ? json.get("per_update_expression").getAsString() : "";
        return new ParticleInitialization(perRender, perUpdate);
    }

    @Override
    public int order() {
        return 360;
    }

    public String getPerRenderExpression() { return perRenderExpression; }
    public String getPerUpdateExpression() { return perUpdateExpression; }
}
