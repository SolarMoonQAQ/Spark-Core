package cn.solarmoon.spark_core.molang.core.variable;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Variable;

public abstract class ContextVariable<TEntity> implements Variable {
    protected boolean validateContext(IContext<?> context) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Object evaluate(ExecutionContext<?> context) {
        Object entity = context.entity();
        if (entity instanceof IContext && validateContext((IContext<?>) entity)) {
            return evaluate((IContext<TEntity>) entity);
        } else {
            return null;
        }
    }

    public abstract Object evaluate(IContext<TEntity> context);
}
