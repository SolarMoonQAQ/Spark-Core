package cn.solarmoon.spark_core.molang.core.value;

import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;

public class RotationValue implements IValue {
    private final IValue value;
    private final boolean flip;

    public RotationValue(IValue value, boolean flip) {
        this.value = value;
        this.flip = flip;
    }

    public static float processValue(double value, boolean flip) {
        float ret = (float) Math.toRadians(value);
        if(flip) {
            ret = -ret;
        }
        return ret;
    }

    @Override
    public double evalAsDouble(ExpressionEvaluator<?> evaluator) {
        return processValue(this.value.evalAsDouble(evaluator), this.flip);
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) {
        return evalAsDouble(evaluator);
    }
}
