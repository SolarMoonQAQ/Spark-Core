// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import net.minecraft.world.entity.LivingEntity;

/**
 * 代表一个生物实体。继承 {@link MolangEntityContext} 的所有查询，
 * 额外提供生命值、护甲、移动速度等 LivingEntity 相关查询。
 *
 * @param <T> 实体类型
 */
@SuppressWarnings("unused")
public class MolangLivingContext<T extends LivingEntity> extends MolangEntityContext<T> {

    public MolangLivingContext() {
    }

    public MolangLivingContext(T entity) {
        super(entity);
    }

    @QueryBinding("health")
    public double health() {
        T e = getEntity();
        return e != null ? (double) e.getHealth() : 0.0;
    }

    @QueryBinding("max_health")
    public double maxHealth() {
        T e = getEntity();
        return e != null ? (double) e.getMaxHealth() : 0.0;
    }

    @QueryBinding("health_ratio")
    public double healthRatio() {
        T e = getEntity();
        return e != null ? e.getHealth() / e.getMaxHealth() : 0.0;
    }

    @QueryBinding("is_baby")
    public double isBaby() {
        T e = getEntity();
        return e != null && e.isBaby() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sleeping")
    public double isSleeping() {
        T e = getEntity();
        return e != null && e.isSleeping() ? 1.0 : 0.0;
    }

    @QueryBinding("is_using_item")
    public double isUsingItem() {
        T e = getEntity();
        return e != null && e.isUsingItem() ? 1.0 : 0.0;
    }

    @QueryBinding("is_blocking")
    public double isBlocking() {
        T e = getEntity();
        return e != null && e.isBlocking() ? 1.0 : 0.0;
    }

    @QueryBinding("is_fall_flying")
    public double isFallFlying() {
        T e = getEntity();
        return e != null && e.isFallFlying() ? 1.0 : 0.0;
    }

    @QueryBinding("movement_speed")
    public double movementSpeed() {
        T e = getEntity();
        return e != null ? (double) e.getSpeed() : 0.0;
    }

    @QueryBinding("scale")
    public double scale() {
        T e = getEntity();
        return e != null ? (double) e.getScale() : 1.0;
    }

    @QueryBinding("armor_value")
    public double armorValue() {
        T e = getEntity();
        return e != null ? (double) e.getArmorValue() : 0.0;
    }

    @QueryBinding("arrow_count")
    public double arrowCount() {
        T e = getEntity();
        return e != null ? (double) e.getArrowCount() : 0.0;
    }

    @QueryBinding("is_hurt")
    public double isHurt() {
        T e = getEntity();
        return e != null && e.hurtDuration > 0 ? 1.0 : 0.0;
    }
}
