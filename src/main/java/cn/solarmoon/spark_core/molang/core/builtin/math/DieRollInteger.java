package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;

import java.util.Random;

public class DieRollInteger extends ContextFunction<Object> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }

    @Override
    protected Object eval(ExecutionContext<IAnimatable<Object>> context, ArgumentCollection arguments) {
        int i = Math.round(arguments.getAsFloat(context, 0));
        int min = arguments.getAsInt(context, 1);
        int range = arguments.getAsInt(context, 2);
        if(min > range) {
            int temp = min;
            min = range;
            range = temp - range;
        } else {
            range -= min;
        }
        int total = 0;
        Random rnd = new Random();
        while (i-- > 0) {
            total += min + rnd.nextInt(range);
        }
        return total;
    }
}
