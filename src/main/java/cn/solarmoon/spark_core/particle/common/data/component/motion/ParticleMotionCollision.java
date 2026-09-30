package cn.solarmoon.spark_core.particle.common.data.component.motion;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子碰撞运动组件。
 * 控制粒子与方块地形的碰撞行为。
 * 对应 JSON key: minecraft:particle_motion_collision
 * <p>
 * 所有数值字段均支持 MoLang 表达式（对齐 Bedrock 规范）。
 * 运行时组件只负责把求值后的参数写入 ParticleArray，扫掠由 CollisionResolver 统一执行。
 */
public class ParticleMotionCollision implements IParticleComponentDefinition {

    /** 单条碰撞事件定义：触发的事件名 + 触发所需的最小速度（方块/秒） */
    public record CollisionEvent(String event, float minSpeed) {}

    /** `events` 的规范缺省 min_speed：Bedrock 文档 "default/minimum is 2 blocks/sec" */
    public static final float DEFAULT_MIN_SPEED = 2f;

    private final String enabled;
    private final String collisionDrag;
    private final String coefficientOfRestitution;
    private final String collisionRadius;
    private final boolean expireOnContact;
    private final List<CollisionEvent> events;
    /** `events` 字段是否存在（用于「存在但解析为空」的加载期告警） */
    private final boolean eventsFieldPresent;

    public ParticleMotionCollision(String enabled, String collisionDrag, String coefficientOfRestitution,
                                    String collisionRadius, boolean expireOnContact,
                                    List<CollisionEvent> events, boolean eventsFieldPresent) {
        this.enabled = enabled;
        this.collisionDrag = collisionDrag;
        this.coefficientOfRestitution = coefficientOfRestitution;
        this.collisionRadius = collisionRadius;
        this.expireOnContact = expireOnContact;
        this.events = events;
        this.eventsFieldPresent = eventsFieldPresent;
    }

    /**
     * 从 JSON 对象反序列化。
     * 所有数值字段均作为 MoLang 表达式字符串读取（同时支持数字字面量）。
     */
    public static ParticleMotionCollision fromJson(JsonObject json) {
        String enabled = json.has("enabled") ? json.get("enabled").getAsString() : "1";
        String drag = json.has("collision_drag") ? json.get("collision_drag").getAsString() : "0";
        String restitution = json.has("coefficient_of_restitution") ? json.get("coefficient_of_restitution").getAsString() : "0";
        String radius = json.has("collision_radius") ? json.get("collision_radius").getAsString() : "0";
        boolean expire = json.has("expire_on_contact") && json.get("expire_on_contact").getAsBoolean();
        return new ParticleMotionCollision(enabled, drag, restitution, radius, expire,
                parseEvents(json), json.has("events"));
    }

    /**
     * 解析 `events` 字段。数组与单对象两种写法都合法（Bedrock 规范二者都给出，
     * 对象形式文档注明 "can be an individual event as well"）；其余类型返回空表，绝不抛异常
     * —— 旧实现用 {@code getAsJsonObject()} 在数组格式下会抛异常，导致整个粒子定义加载失败。
     */
    private static List<CollisionEvent> parseEvents(JsonObject obj) {
        if (!obj.has("events")) return List.of();
        JsonElement element = obj.get("events");
        List<CollisionEvent> list = new ArrayList<>(1);
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) addEvent(item, list);
        } else {
            addEvent(element, list);
        }
        return list.isEmpty() ? List.of() : list;
    }

    private static void addEvent(JsonElement element, List<CollisionEvent> out) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject o = element.getAsJsonObject();
        String event = o.has("event") ? o.get("event").getAsString() : "";
        float minSpeed = o.has("min_speed") ? o.get("min_speed").getAsFloat() : DEFAULT_MIN_SPEED;
        if (!event.isEmpty()) out.add(new CollisionEvent(event, minSpeed));
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
    public List<CollisionEvent> getEvents() { return events; }
    public boolean isEventsFieldPresent() { return eventsFieldPresent; }
}
