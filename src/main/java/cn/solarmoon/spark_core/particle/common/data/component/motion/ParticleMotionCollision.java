package cn.solarmoon.spark_core.particle.common.data.component.motion;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 粒子碰撞运动组件。
 * 控制粒子与方块/实体的碰撞行为。
 * 对应 JSON key: minecraft:particle_motion_collision
 * <p>
 * 所有数值字段均支持 MoLang 表达式（对齐 Bedrock 规范）。
 */
public class ParticleMotionCollision implements IParticleComponentDefinition {

    private final String enabled;
    private final String collisionDrag;
    private final String coefficientOfRestitution;
    private final String collisionRadius;
    private final boolean expireOnContact;
    private final JsonObject events;

    public ParticleMotionCollision(String enabled, String collisionDrag, String coefficientOfRestitution,
                                    String collisionRadius, boolean expireOnContact, JsonObject events) {
        this.enabled = enabled;
        this.collisionDrag = collisionDrag;
        this.coefficientOfRestitution = coefficientOfRestitution;
        this.collisionRadius = collisionRadius;
        this.expireOnContact = expireOnContact;
        this.events = events;
    }

    /**
     * 从 JSON 对象反序列化。
     * 对标 SBM：所有数值字段均作为 MoLang 表达式字符串读取。
     */
    public static ParticleMotionCollision fromJson(JsonObject json) {
        String enabled = json.has("enabled") ? json.get("enabled").getAsString() : "1";
        // 使用 getAsString 同时支持数字字面量和 MoLang 表达式字符串
        String drag = json.has("collision_drag") ? json.get("collision_drag").getAsString() : "0";
        String restitution = json.has("coefficient_of_restitution") ? json.get("coefficient_of_restitution").getAsString() : "0";
        String radius = json.has("collision_radius") ? json.get("collision_radius").getAsString() : "0";
        boolean expire = json.has("expire_on_contact") && json.get("expire_on_contact").getAsBoolean();
        JsonObject evt = json.has("events") ? json.getAsJsonObject("events") : new JsonObject();
        return new ParticleMotionCollision(enabled, drag, restitution, radius, expire, evt);
    }

    @Override
    public int order() {
        return 310;
    }

    // --- Getter ---

    public String getEnabled() { return enabled; }
    public String getCollisionDrag() { return collisionDrag; }
    public String getCoefficientOfRestitution() { return coefficientOfRestitution; }
    public String getCollisionRadius() { return collisionRadius; }
    public boolean isExpireOnContact() { return expireOnContact; }
    public JsonObject getEvents() { return events; }
}
