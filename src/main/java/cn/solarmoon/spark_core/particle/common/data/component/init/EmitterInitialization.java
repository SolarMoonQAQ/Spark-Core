package cn.solarmoon.spark_core.particle.common.data.component.init;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 发射器初始化组件。<br>
 * 在发射器创建时和每帧更新时执行 Molang 表达式，
 * 用于设置发射器级的 Molang 变量。
 * 对应 JSON key: minecraft:emitter_initialization
 */
public class EmitterInitialization implements IEmitterComponentDefinition {

    private final String creationExpression;
    private final String perUpdateExpression;

    public EmitterInitialization(String creationExpression, String perUpdateExpression) {
        this.creationExpression = creationExpression;
        this.perUpdateExpression = perUpdateExpression;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterInitialization fromJson(JsonObject json) {
        String creation = json.has("creation_expression") ? json.get("creation_expression").getAsString() : "";
        String perUpdate = json.has("per_update_expression") ? json.get("per_update_expression").getAsString() : "";
        return new EmitterInitialization(creation, perUpdate);
    }

    @Override
    public int order() {
        return 505;
    }

    public String getCreationExpression() {
        return creationExpression;
    }

    public String getPerUpdateExpression() {
        return perUpdateExpression;
    }
}
