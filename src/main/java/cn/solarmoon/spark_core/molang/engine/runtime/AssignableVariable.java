package cn.solarmoon.spark_core.molang.engine.runtime;

import org.jetbrains.annotations.NotNull;

public interface AssignableVariable extends Variable {
    void assign(final @NotNull ExecutionContext<?> context, Object value);
}
