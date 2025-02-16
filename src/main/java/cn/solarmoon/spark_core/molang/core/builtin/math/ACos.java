package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Function;

public class ACos implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Math.acos(arguments.getAsDouble(context, 0));
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
