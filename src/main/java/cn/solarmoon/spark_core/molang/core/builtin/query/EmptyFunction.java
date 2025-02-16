package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;

public class EmptyFunction extends ContextFunction<Object> {
    @Override
    protected Object eval(ExecutionContext<IContext<Object>> context, ArgumentCollection arguments) {
        return null;
    }
}
