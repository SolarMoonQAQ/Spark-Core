package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.particle.common.data.component.EmitterLocalSpace;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceBillboard;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceLighting;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceTinting;
import cn.solarmoon.spark_core.particle.common.data.component.expire.ParticleExpireIfInBlocks;
import cn.solarmoon.spark_core.particle.common.data.component.expire.ParticleExpireIfNotInBlocks;
import cn.solarmoon.spark_core.particle.common.data.component.init.EmitterInitialization;
import cn.solarmoon.spark_core.particle.common.data.component.init.ParticleInitialSpeed;
import cn.solarmoon.spark_core.particle.common.data.component.init.ParticleInitialSpin;
import cn.solarmoon.spark_core.particle.common.data.component.init.ParticleInitialization;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.*;
import cn.solarmoon.spark_core.particle.common.data.component.motion.*;
import cn.solarmoon.spark_core.particle.common.data.component.rate.*;
import cn.solarmoon.spark_core.particle.common.data.component.shape.*;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 粒子组件注册表。JSON key → 反序列化工厂。
 * <p>
 * 对标 SBM 模式，反序列化接口接收原始 {@link JsonElement}（可为对象、数组、字符串、数字），
 * 各组件的 {@code fromJson} 自行判断类型并解析。
 */
public class ParticleComponentRegistry {

    @FunctionalInterface
    public interface ComponentDeserializer {
        /** 从原始 JsonElement 反序列化组件定义 */
        @Nullable
        IComponentDefinition deserialize(String key, JsonElement value);
    }

    private static final Map<String, ComponentDeserializer> DESERIALIZERS = new HashMap<>();

    static {
        // 发射器速率
        register("minecraft:emitter_rate_instant", (k, v) -> EmitterRateInstant.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_rate_steady", (k, v) -> EmitterRateSteady.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_rate_manual", (k, v) -> EmitterRateManual.fromJson(v.getAsJsonObject()));

        // 发射器生命周期
        register("minecraft:emitter_lifetime_once", (k, v) -> EmitterLifetimeOnce.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_lifetime_looping", (k, v) -> EmitterLifetimeLooping.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_lifetime_expression", (k, v) -> EmitterLifetimeExpression.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_lifetime_events", (k, v) -> EmitterLifetimeEvents.fromJson(v.getAsJsonObject()));

        // 发射器初始化
        register("minecraft:emitter_initialization", (k, v) -> EmitterInitialization.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_local_space", (k, v) -> EmitterLocalSpace.fromJson(v.getAsJsonObject()));

        // 发射器形状
        register("minecraft:emitter_shape_point", (k, v) -> EmitterShapePoint.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_shape_sphere", (k, v) -> EmitterShapeSphere.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_shape_box", (k, v) -> EmitterShapeBox.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_shape_disc", (k, v) -> EmitterShapeDisc.fromJson(v.getAsJsonObject()));
        register("minecraft:emitter_shape_entity_aabb", (k, v) -> EmitterShapeEntityAABB.fromJson(v.getAsJsonObject()));

        // 粒子生命周期
        register("minecraft:particle_lifetime_expression", (k, v) -> ParticleLifetimeExpression.fromJson(v.getAsJsonObject()));
        register("minecraft:particle_lifetime_kill_plane", (k, v) -> ParticleLifetimeKillPlane.fromJson(v)); // 裸数组
        register("minecraft:particle_lifetime_events", (k, v) -> ParticleLifetimeEvents.fromJson(v.getAsJsonObject()));

        // 粒子初始化
        register("minecraft:particle_initial_speed", (k, v) -> ParticleInitialSpeed.fromJson(v)); // 可为裸数字/字符串
        register("minecraft:particle_initial_spin", (k, v) -> ParticleInitialSpin.fromJson(v.getAsJsonObject()));
        register("minecraft:particle_initialization", (k, v) -> ParticleInitialization.fromJson(v.getAsJsonObject()));

        // 粒子运动
        register("minecraft:particle_motion_dynamic", (k, v) -> ParticleMotionDynamic.fromJson(v.getAsJsonObject()));
        register("minecraft:particle_motion_parametric", (k, v) -> ParticleMotionParametric.fromJson(v.getAsJsonObject()));
        register("minecraft:particle_motion_collision", (k, v) -> ParticleMotionCollision.fromJson(v.getAsJsonObject()));

        // 粒子外观
        register("minecraft:particle_appearance_billboard", (k, v) -> ParticleAppearanceBillboard.fromJson(v.getAsJsonObject()));
        register("minecraft:particle_appearance_tinting", (k, v) -> ParticleAppearanceTinting.fromJson(v.getAsJsonObject()));
        register("minecraft:particle_appearance_lighting", (k, v) -> ParticleAppearanceLighting.fromJson(v.getAsJsonObject()));

        // 粒子过期条件 — 裸数组格式
        register("minecraft:particle_expire_if_in_blocks", (k, v) -> ParticleExpireIfInBlocks.fromJson(v));
        register("minecraft:particle_expire_if_not_in_blocks", (k, v) -> ParticleExpireIfNotInBlocks.fromJson(v));
    }

    private static void register(String key, ComponentDeserializer deserializer) {
        DESERIALIZERS.put(key, deserializer);
    }

    @Nullable
    public static IComponentDefinition deserialize(String key, JsonElement value) {
        ComponentDeserializer fn = DESERIALIZERS.get(key);
        if (fn == null) return null;
        return fn.deserialize(key, value);
    }

    public static boolean isRegistered(String key) {
        return DESERIALIZERS.containsKey(key);
    }
}
