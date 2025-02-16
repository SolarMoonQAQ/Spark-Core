package cn.solarmoon.spark_core.molang.engine.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Variable {
    @Nullable Object evaluate(final @NotNull ExecutionContext<?> context);
}
