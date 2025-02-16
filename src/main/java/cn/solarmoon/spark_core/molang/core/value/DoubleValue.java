package cn.solarmoon.spark_core.molang.core.value;

import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;

public class DoubleValue implements IValue {
    public static final DoubleValue ONE = new DoubleValue(1);
    public static final DoubleValue ZERO = new DoubleValue(0);

    private final double value;

    public DoubleValue(double value) {
        this.value = value;
    }

    @Override
    public double evalAsDouble(ExpressionEvaluator<?> evaluator) {
        return value;
    }

    @Override
    public boolean evalAsBoolean(ExpressionEvaluator<?> evaluator) {
        return value != 0;
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) {
        return value;
    }

    public double value() {
        return value;
    }
}
