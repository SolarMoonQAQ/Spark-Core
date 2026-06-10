package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonObject;

/**
 * 粒子生命周期表达式组件。
 * 通过 Molang 表达式控制粒子的最大生命周期和过期条件。
 * 对应 JSON key: minecraft:particle_lifetime_expression
 */
public class ParticleLifetimeExpression implements IParticleComponentDefinition {

    private final String maxLifetime;
    private final String expirationExpression;

    public ParticleLifetimeExpression(String maxLifetime, String expirationExpression) {
        this.maxLifetime = maxLifetime;
        this.expirationExpression = expirationExpression;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleLifetimeExpression fromJson(JsonObject json) {
        String maxLife = json.has("max_lifetime") ? json.get("max_lifetime").getAsString() : "1";
        String expiration = json.has("expiration_expression") ? json.get("expiration_expression").getAsString() : "0";
        return new ParticleLifetimeExpression(maxLife, expiration);
    }

    @Override
    public int order() {
        return 0;
    }

    public String getMaxLifetime() { return maxLifetime; }
    public String getExpirationExpression() { return expirationExpression; }
}
