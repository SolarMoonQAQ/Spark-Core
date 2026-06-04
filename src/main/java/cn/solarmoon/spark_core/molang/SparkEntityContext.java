// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import net.minecraft.world.entity.Entity;

/**
 * 实体动画的 MoLang 求值上下文，对标 {@link IEntityAnimatable}。
 * <p>
 * 泛型 A 是 {@link IEntityAnimatable} 子类型，通过
 * {@code getEntity().getAnimatable()} 可直接获取 {@link Entity} 对象。
 * <p>
 * 提供 {@code query.is_on_ground}、{@code query.position_x} 等
 * 所有常见 Entity 属性的 @QueryBinding 绑定。
 *
 * @param <A> 继承 {@link IEntityAnimatable} 的具体类型
 */
@SuppressWarnings("unused")
public class SparkEntityContext<A extends IEntityAnimatable<?>> extends SparkMolangContext<A> {

    public SparkEntityContext() {
    }

    public SparkEntityContext(A animatable) {
        super(animatable);
    }

    /** 从 getEntity() 获取原始的 Entity 对象，null 安全返回 0.0/false。 */
    private Entity entity() {
        A a = getEntity();
        return a != null ? a.getAnimatable() : null;
    }

    // ==================== Entity 状态 ====================

    @QueryBinding("is_on_ground")
    public double isOnGround() {
        Entity e = entity();
        return e != null && e.onGround() ? 1.0 : 0.0;
    }

    @QueryBinding("is_alive")
    public double isAlive() {
        Entity e = entity();
        return e != null && e.isAlive() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_water")
    public double isInWater() {
        Entity e = entity();
        return e != null && e.isInWater() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_water_or_rain")
    public double isInWaterOrRain() {
        Entity e = entity();
        return e != null && e.isInWaterRainOrBubble() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_lava")
    public double isInLava() {
        Entity e = entity();
        return e != null && e.isInLava() ? 1.0 : 0.0;
    }

    @QueryBinding("is_on_fire")
    public double isOnFire() {
        Entity e = entity();
        return e != null && e.isOnFire() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sprinting")
    public double isSprinting() {
        Entity e = entity();
        return e != null && e.isSprinting() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sneaking")
    public double isSneaking() {
        Entity e = entity();
        return e != null && e.isCrouching() ? 1.0 : 0.0;
    }

    @QueryBinding("is_swimming")
    public double isSwimming() {
        Entity e = entity();
        return e != null && e.isSwimming() ? 1.0 : 0.0;
    }

    @QueryBinding("is_riding")
    public double isRiding() {
        Entity e = entity();
        return e != null && e.isPassenger() ? 1.0 : 0.0;
    }

    @QueryBinding("is_silent")
    public double isSilent() {
        Entity e = entity();
        return e != null && e.isSilent() ? 1.0 : 0.0;
    }

    @QueryBinding("is_no_gravity")
    public double isNoGravity() {
        Entity e = entity();
        return e != null && e.isNoGravity() ? 1.0 : 0.0;
    }

    @QueryBinding("is_invisible")
    public double isInvisible() {
        Entity e = entity();
        return e != null && e.isInvisible() ? 1.0 : 0.0;
    }

    @QueryBinding("is_glowing")
    public double isGlowing() {
        Entity e = entity();
        return e != null && e.isCurrentlyGlowing() ? 1.0 : 0.0;
    }

    // ==================== Entity 属性 ====================

    @QueryBinding("life_time")
    public double lifeTime() {
        Entity e = entity();
        return e != null ? (double) e.tickCount : 0.0;
    }

    @QueryBinding("body_y_rotation")
    public double bodyYRotation() {
        Entity e = entity();
        return e != null ? (double) e.getYRot() : 0.0;
    }

    // ==================== 位置 ====================

    @QueryBinding("position_x")
    public double positionX() {
        Entity e = entity();
        return e != null ? e.getX() : 0.0;
    }

    @QueryBinding("position_y")
    public double positionY() {
        Entity e = entity();
        return e != null ? e.getY() : 0.0;
    }

    @QueryBinding("position_z")
    public double positionZ() {
        Entity e = entity();
        return e != null ? e.getZ() : 0.0;
    }

    // ==================== Level / 时间天气 ====================

    @QueryBinding("time_of_day")
    public double timeOfDay() {
        Entity e = entity();
        return e != null ? (double) (e.level().getDayTime() % 24000) : 0.0;
    }

    @QueryBinding("time_stamp")
    public double timeStamp() {
        Entity e = entity();
        return e != null ? (double) e.level().getGameTime() : 0.0;
    }

    @QueryBinding("is_day")
    public double isDay() {
        Entity e = entity();
        return e != null && e.level().isDay() ? 1.0 : 0.0;
    }

    @QueryBinding("is_night")
    public double isNight() {
        Entity e = entity();
        return e != null && e.level().isNight() ? 1.0 : 0.0;
    }

    @QueryBinding("is_raining")
    public double isRaining() {
        Entity e = entity();
        return e != null && e.level().isRaining() ? 1.0 : 0.0;
    }

    @QueryBinding("is_thundering")
    public double isThundering() {
        Entity e = entity();
        return e != null && e.level().isThundering() ? 1.0 : 0.0;
    }

    @QueryBinding("moon_phase")
    public double moonPhase() {
        Entity e = entity();
        return e != null ? (double) e.level().getMoonPhase() : 0.0;
    }
}
