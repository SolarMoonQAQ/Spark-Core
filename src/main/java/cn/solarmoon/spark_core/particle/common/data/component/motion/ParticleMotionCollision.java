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
 */
public class ParticleMotionCollision implements IParticleComponentDefinition {

    private final String enabled;
    private final float collisionDrag;
    private final float coefficientOfRestitution;
    private final float collisionRadius;
    private final boolean expireOnContact;
    private final JsonObject events;

    public ParticleMotionCollision(String enabled, float collisionDrag, float coefficientOfRestitution,
                                    float collisionRadius, boolean expireOnContact, JsonObject events) {
        this.enabled = enabled;
        this.collisionDrag = collisionDrag;
        this.coefficientOfRestitution = coefficientOfRestitution;
        this.collisionRadius = collisionRadius;
        this.expireOnContact = expireOnContact;
        this.events = events;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleMotionCollision fromJson(JsonObject json) {
        String enabled = json.has("enabled") ? json.get("enabled").getAsString() : "1";
        float drag = json.has("collision_drag") ? json.get("collision_drag").getAsFloat() : 0f;
        float restitution = json.has("coefficient_of_restitution") ? json.get("coefficient_of_restitution").getAsFloat() : 0f;
        float radius = json.has("collision_radius") ? json.get("collision_radius").getAsFloat() : 0f;
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
    public float getCollisionDrag() { return collisionDrag; }
    public float getCoefficientOfRestitution() { return coefficientOfRestitution; }
    public float getCollisionRadius() { return collisionRadius; }
    public boolean isExpireOnContact() { return expireOnContact; }
    public JsonObject getEvents() { return events; }
}
