package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;

import java.util.Random;

public class DieRoll extends ContextFunction<Object> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }

    @Override
    protected Object eval(ExecutionContext<IAnimatable<Object>> context, ArgumentCollection arguments) {
        int i = arguments.getAsInt(context, 0);
        double min = arguments.getAsDouble(context, 1);
        double range = arguments.getAsDouble(context, 2);
        if(min > range) {
            double temp = min;
            min = range;
            range = temp - range;
        } else {
            range -= min;
        }
        double total = 0;
        Random rnd = context.entity().getRandomSeed();
        while (i-- > 0) {
            total += min + rnd.nextDouble() * range;
        }
        return total;
    }
}
