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
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 粒子组件注册表。JSON key → 反序列化工厂。
 */
public class ParticleComponentRegistry {

    private static final Map<String, Function<JsonObject, IComponentDefinition>> DESERIALIZERS = new HashMap<>();

    static {
        // 发射器速率
        register("minecraft:emitter_rate_instant", EmitterRateInstant::fromJson);
        register("minecraft:emitter_rate_steady", EmitterRateSteady::fromJson);
        register("minecraft:emitter_rate_manual", EmitterRateManual::fromJson);

        // 发射器生命周期
        register("minecraft:emitter_lifetime_once", EmitterLifetimeOnce::fromJson);
        register("minecraft:emitter_lifetime_looping", EmitterLifetimeLooping::fromJson);
        register("minecraft:emitter_lifetime_expression", EmitterLifetimeExpression::fromJson);
        register("minecraft:emitter_lifetime_events", EmitterLifetimeEvents::fromJson);

        // 发射器初始化
        register("minecraft:emitter_initialization", EmitterInitialization::fromJson);
        register("minecraft:emitter_local_space", EmitterLocalSpace::fromJson);

        // 发射器形状
        register("minecraft:emitter_shape_point", EmitterShapePoint::fromJson);
        register("minecraft:emitter_shape_sphere", EmitterShapeSphere::fromJson);
        register("minecraft:emitter_shape_box", EmitterShapeBox::fromJson);
        register("minecraft:emitter_shape_disc", EmitterShapeDisc::fromJson);
        register("minecraft:emitter_shape_entity_aabb", EmitterShapeEntityAABB::fromJson);

        // 粒子生命周期
        register("minecraft:particle_lifetime_expression", ParticleLifetimeExpression::fromJson);
        register("minecraft:particle_lifetime_kill_plane", ParticleLifetimeKillPlane::fromJson);
        register("minecraft:particle_lifetime_events", ParticleLifetimeEvents::fromJson);

        // 粒子初始化
        register("minecraft:particle_initial_speed", ParticleInitialSpeed::fromJson);
        register("minecraft:particle_initial_spin", ParticleInitialSpin::fromJson);
        register("minecraft:particle_initialization", ParticleInitialization::fromJson);

        // 粒子运动
        register("minecraft:particle_motion_dynamic", ParticleMotionDynamic::fromJson);
        register("minecraft:particle_motion_parametric", ParticleMotionParametric::fromJson);
        register("minecraft:particle_motion_collision", ParticleMotionCollision::fromJson);

        // 粒子外观
        register("minecraft:particle_appearance_billboard", ParticleAppearanceBillboard::fromJson);
        register("minecraft:particle_appearance_tinting", ParticleAppearanceTinting::fromJson);
        register("minecraft:particle_appearance_lighting", ParticleAppearanceLighting::fromJson);

        // 粒子过期条件
        register("minecraft:particle_expire_if_in_blocks", ParticleExpireIfInBlocks::fromJson);
        register("minecraft:particle_expire_if_not_in_blocks", ParticleExpireIfNotInBlocks::fromJson);
    }

    private static void register(String key, Function<JsonObject, IComponentDefinition> deserializer) {
        DESERIALIZERS.put(key, deserializer);
    }

    @Nullable
    public static IComponentDefinition deserialize(String key, JsonObject json) {
        Function<JsonObject, IComponentDefinition> fn = DESERIALIZERS.get(key);
        if (fn == null) return null;
        return fn.apply(json);
    }

    public static boolean isRegistered(String key) {
        return DESERIALIZERS.containsKey(key);
    }
}
