package cn.solarmoon.spark_core.molang.core.variable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Variable;

public abstract class ContextVariable<TEntity> implements Variable {
    protected boolean validateContext(IAnimatable<?> context) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Object evaluate(ExecutionContext<?> context) {
        Object entity = context.entity();
        if (entity instanceof IAnimatable && validateContext((IAnimatable<?>) entity)) {
            return evaluate((IAnimatable<TEntity>) entity);
        } else {
            return null;
        }
    }

    public abstract Object evaluate(IAnimatable<TEntity> context);
}
