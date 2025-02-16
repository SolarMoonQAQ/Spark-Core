package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;

public class Random extends ContextFunction<Object> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;  // 出于兼容性考虑，允许 3 参数
    }

    @Override
    protected Object eval(ExecutionContext<IAnimatable<Object>> context, ArgumentCollection arguments) {
        double min = arguments.getAsDouble(context, 0);
        double range = arguments.getAsDouble(context, 1);
        if(min > range) {
            double temp = min;
            min = range;
            range = temp - range;
        } else {
            range -= min;
        }
        return min + context.entity().getRandomSeed().nextDouble() * range;
    }
}
