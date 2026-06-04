// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang.runtime;

import cn.solarmoon.spark_core.molang.runtime.binding.Entity;
import cn.solarmoon.spark_core.molang.runtime.compiled.MochaCompiledFunction;

/**
 * 带上下文参数的 Molang 编译函数。
 * <p>
 * 与 {@link cn.solarmoon.spark_core.molang.runtime.MochaFunction MochaFunction}
 * 不同，此接口的 evaluate 方法接收 {@link MolangContext} 参数，
 * 使得同一个编译后的表达式可以对不同 entity/上下文复用。
 */
@FunctionalInterface
public interface MolangExpression extends MochaCompiledFunction {
    double evaluate(@Entity MolangContext<?> context);

    /**
     * 返回一个始终求值为 0 的空表达式。
     */
    static MolangExpression zero() {
        return ctx -> 0D;
    }

    /**
     * 返回一个始终求值为指定常量的表达式。
     */
    static MolangExpression constant(double value) {
        return ctx -> value;
    }
}
