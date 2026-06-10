package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 表达式控制发射生命周期组件。<br>
 * 通过 activation_expression 和 expiration_expression 两个 Molang 表达式
 * 控制发射器的激活与过期状态。
 * 对应 JSON key: minecraft:emitter_lifetime_expression
 */
public class EmitterLifetimeExpression implements IEmitterComponentDefinition {

    private final String activationExpression;
    private final String expirationExpression;

    public EmitterLifetimeExpression(String activationExpression, String expirationExpression) {
        this.activationExpression = activationExpression;
        this.expirationExpression = expirationExpression;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterLifetimeExpression fromJson(JsonObject json) {
        String activation = json.has("activation_expression") ? json.get("activation_expression").getAsString() : "1";
        String expiration = json.has("expiration_expression") ? json.get("expiration_expression").getAsString() : "0";
        return new EmitterLifetimeExpression(activation, expiration);
    }

    @Override
    public int order() {
        return 500;
    }

    public String getActivationExpression() {
        return activationExpression;
    }

    public String getExpirationExpression() {
        return expirationExpression;
    }
}
