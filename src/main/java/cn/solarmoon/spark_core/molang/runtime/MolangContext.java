// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang.runtime;

import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.value.MutableObjectBinding;
import org.jetbrains.annotations.Nullable;

/**
 * Molang 求值上下文。持有关联对象的引用、独立的 variable 存储。
 *
 * @param <T> 关联对象的类型
 */
public class MolangContext<T> {
    private @Nullable T entity;
    private final MutableObjectBinding variableStorage;
    private double animTime;

    public MolangContext() {
        this(null, new MutableObjectBinding());
    }

    public MolangContext(@Nullable T entity) {
        this(entity, new MutableObjectBinding());
    }

    public MolangContext(@Nullable T entity, MutableObjectBinding variableStorage) {
        this.entity = entity;
        this.variableStorage = variableStorage;
    }

    @Nullable
    public T getEntity() {
        return entity;
    }

    public void setEntity(@Nullable T entity) {
        this.entity = entity;
    }

    public float getTimeS() {
        return (float) animTime;
    }

    public void prepareEvaluation(float timeS) {
        this.animTime = timeS;
    }

    // === @QueryBinding 属性 ===

    /**
     * 当前动画播放时间（秒），对应 Molang {@code query.anim_time}。
     */
    @QueryBinding("anim_time")
    public double queryAnimTime() {
        return animTime;
    }

    // === Variable 存储 ===
    /**
     * 获取此 context 独立的 variable 存储。
     * 对应 Molang 中的 variable / v 命名空间。
     */
    public MutableObjectBinding getVariableStorage() {
        return variableStorage;
    }
}
