// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.runtime.value.ObjectProperty;
import cn.solarmoon.spark_core.molang.runtime.value.ObjectValue;
import cn.solarmoon.spark_core.molang.runtime.value.StringValue;
import cn.solarmoon.spark_core.molang.runtime.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 将 {@link IAnimatable#variables} 适配为 Mocha {@link ObjectValue}。
 * <p>
 * variable 的读写直接代理到 animatable 的变量 Map：
 * <ul>
 *   <li>{@code variable.x = 5} → {@code animatable.putVariable("x", 5.0)}</li>
 *   <li>{@code variable.x} → {@code animatable.variables["x"]}</li>
 * </ul>
 * 所有 MoLang 求值都在物理线程，不存在真实并发冲突。
 */
public class AnimatableVariableBinding implements ObjectValue {
    private IAnimatable<?> animatable;

    public AnimatableVariableBinding(@Nullable IAnimatable<?> animatable) {
        this.animatable = animatable;
    }

    public void setAnimatable(@Nullable IAnimatable<?> animatable) {
        this.animatable = animatable;
    }

    @Override
    public @Nullable ObjectProperty getProperty(@NotNull String name) {
        if (animatable == null) return null;
        Object val = animatable.getVariables().get(name.toLowerCase());
        return ObjectProperty.property(Value.of(val), false);
    }

    @Override
    public @NotNull Value get(@NotNull String name) {
        if (animatable == null) return Value.nil();
        Object val = animatable.getVariables().get(name.toLowerCase());
        return Value.of(val);
    }

    @Override
    public boolean set(@NotNull String name, @Nullable Value value) {
        if (animatable == null || value == null) return false;
        Object converted;
        if (value instanceof StringValue) {
            converted = value.getAsString();
        } else if (value.getAsBoolean()) {
            converted = value.getAsNumber(); // true → 1.0
        } else {
            converted = value.getAsNumber(); // false → 0.0, 数字保持原样
        }
        animatable.putVariable(name.toLowerCase(), converted);
        return true;
    }

    @Override
    public @NotNull Map<String, ObjectProperty> entries() {
        if (animatable == null) return Map.of();
        Map<String, ObjectProperty> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : animatable.getVariables().entrySet()) {
            result.put(entry.getKey(), ObjectProperty.property(Value.of(entry.getValue()), false));
        }
        return result;
    }
}
