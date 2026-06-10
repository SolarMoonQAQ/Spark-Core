package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 粒子生命周期事件组件。
 * 定义粒子创建时、过期时以及特定时间点的触发事件。
 * 对应 JSON key: minecraft:particle_lifetime_events
 */
public class ParticleLifetimeEvents implements IParticleComponentDefinition {

    private final String creationEvent;
    private final String expirationEvent;
    private final Map<Float, String> timeline;

    public ParticleLifetimeEvents(String creationEvent, String expirationEvent, Map<Float, String> timeline) {
        this.creationEvent = creationEvent;
        this.expirationEvent = expirationEvent;
        this.timeline = timeline;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleLifetimeEvents fromJson(JsonObject json) {
        String creation = json.has("creation_event") ? json.get("creation_event").getAsString() : "";
        String expiration = json.has("expiration_event") ? json.get("expiration_event").getAsString() : "";

        Map<Float, String> timeline = new HashMap<>();
        if (json.has("timeline")) {
            JsonObject tl = json.getAsJsonObject("timeline");
            for (Map.Entry<String, JsonElement> entry : tl.entrySet()) {
                try {
                    float key = Float.parseFloat(entry.getKey());
                    timeline.put(key, entry.getValue().getAsString());
                } catch (NumberFormatException ignored) {
                    // 忽略无效的时间点键
                }
            }
        }

        return new ParticleLifetimeEvents(creation, expiration, timeline);
    }

    @Override
    public int order() {
        return 310;
    }

    public String getCreationEvent() { return creationEvent; }
    public String getExpirationEvent() { return expirationEvent; }
    public Map<Float, String> getTimeline() { return timeline; }
}
