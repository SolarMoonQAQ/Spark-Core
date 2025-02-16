package cn.solarmoon.spark_core.molang.core.function;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ContextFunction<TEntity> implements Function {
    protected boolean validateContext(IAnimatable<?> context) {
        return true;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public final Object evaluate(@NotNull ExecutionContext<?> context, @NotNull ArgumentCollection arguments) {
        Object entity = context.entity();
        if (entity instanceof IAnimatable && validateContext((IAnimatable<?>) entity)) {
            return eval((ExecutionContext<IAnimatable<TEntity>>) context, arguments);
        } else {
            return null;
        }
    }

    protected abstract Object eval(ExecutionContext<IAnimatable<TEntity>> context, ArgumentCollection arguments);
}
