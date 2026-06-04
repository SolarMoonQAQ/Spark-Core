// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang.runtime;

import cn.solarmoon.spark_core.molang.runtime.binding.Entity;
import cn.solarmoon.spark_core.molang.runtime.compiled.MochaCompiledFunction;

/**
 * 带上下文参数的 Molang 字符串编译函数。
 * <p>
 * 与 {@link MolangExpression} 并列，但 evaluate 返回 {@link String} 而非 double，
 * 使得 {@code vehicle.get('gear')??'NO GBX'} 等含字符串的 MoLang 表达式可直接求值。
 * <p>
 * {@code null} 返回值表示空值，触发 {@code ??} 兜底逻辑。
 */
@FunctionalInterface
public interface MolangStringExpression extends MochaCompiledFunction {
    String evaluate(@Entity MolangContext<?> context);

    static MolangStringExpression nil() {
        return ctx -> null;
    }
}
