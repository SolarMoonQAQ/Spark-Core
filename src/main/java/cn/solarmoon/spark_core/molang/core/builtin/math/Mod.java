package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Function;

public class Mod implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return arguments.getAsDouble(context, 0) % arguments.getAsDouble(context, 1);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2;
    }
}
