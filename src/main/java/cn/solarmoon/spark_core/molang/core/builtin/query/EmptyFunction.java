package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;

public class EmptyFunction extends ContextFunction<Object> {
    @Override
    protected Object eval(ExecutionContext<IAnimatable<Object>> context, ArgumentCollection arguments) {
        return null;
    }
}
