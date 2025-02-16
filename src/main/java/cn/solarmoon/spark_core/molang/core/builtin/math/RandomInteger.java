package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;

public class RandomInteger extends ContextFunction<Object> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2;
    }

    @Override
    protected Object eval(ExecutionContext<IContext<Object>> context, ArgumentCollection arguments) {
        int min = arguments.getAsInt(context, 0);
        int range = arguments.getAsInt(context, 1);
        if(min > range) {
            int temp = min;
            min = range;
            range = temp - range;
        } else {
            range -= min;
        }
        return min + context.entity().random().nextInt(range);
    }
}
