package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 事件驱动发射生命周期组件。<br>
 * 通过 creation_event、expiration_event、timeline 等事件控制发射器生命周期。
 * 对应 JSON key: minecraft:emitter_lifetime_events
 */
public class EmitterLifetimeEvents implements IEmitterComponentDefinition {

    private final String creationEvent;
    private final String expirationEvent;
    private final Map<Float, String> timeline;
    private final Map<Float, String> travelDistanceEvents;
    private final Map<Float, String> loopingTravelDistanceEvents;

    public EmitterLifetimeEvents(String creationEvent, String expirationEvent,
                                  Map<Float, String> timeline,
                                  Map<Float, String> travelDistanceEvents,
                                  Map<Float, String> loopingTravelDistanceEvents) {
        this.creationEvent = creationEvent;
        this.expirationEvent = expirationEvent;
        this.timeline = timeline;
        this.travelDistanceEvents = travelDistanceEvents;
        this.loopingTravelDistanceEvents = loopingTravelDistanceEvents;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static EmitterLifetimeEvents fromJson(JsonObject json) {
        String creation = json.has("creation_event") ? json.get("creation_event").getAsString() : "";
        String expiration = json.has("expiration_event") ? json.get("expiration_event").getAsString() : "";
        Map<Float, String> timeline = parseEventMap(json, "timeline");
        Map<Float, String> travelDist = parseEventMap(json, "travel_distance_events");
        Map<Float, String> loopingTravel = parseEventMap(json, "looping_travel_distance_events");
        return new EmitterLifetimeEvents(creation, expiration, timeline, travelDist, loopingTravel);
    }

    /**
     * 解析 JSON 中浮点数键到字符串值的事件映射。
     */
    private static Map<Float, String> parseEventMap(JsonObject json, String key) {
        if (!json.has(key)) return Collections.emptyMap();
        JsonObject obj = json.getAsJsonObject(key);
        Map<Float, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            float k = Float.parseFloat(entry.getKey());
            result.put(k, entry.getValue().getAsString());
        }
        return result;
    }

    @Override
    public int order() {
        return 510;
    }

    public String getCreationEvent() {
        return creationEvent;
    }

    public String getExpirationEvent() {
        return expirationEvent;
    }

    public Map<Float, String> getTimeline() {
        return timeline;
    }

    public Map<Float, String> getTravelDistanceEvents() {
        return travelDistanceEvents;
    }

    public Map<Float, String> getLoopingTravelDistanceEvents() {
        return loopingTravelDistanceEvents;
    }
}
