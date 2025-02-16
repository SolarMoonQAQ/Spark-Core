package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Function;

public class Trunc implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        double value = arguments.getAsDouble(context, 0);
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
