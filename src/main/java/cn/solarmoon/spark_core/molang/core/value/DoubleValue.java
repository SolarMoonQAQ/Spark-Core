package cn.solarmoon.spark_core.molang.core.value;

import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;
import com.mojang.serialization.Codec;

public record DoubleValue(double value) implements IValue {
    public static final DoubleValue ONE = new DoubleValue(1);
    public static final DoubleValue ZERO = new DoubleValue(0);
    public static final Codec<IValue> DOUBLE_VALUE_CODEC = Codec.DOUBLE.xmap(
            DoubleValue::new,
            DoubleValue::getValue
    );

    public static double getValue(IValue value) {
        if (value instanceof DoubleValue) {
            return ((DoubleValue) value).value;
        } else return 0;
    }

    @Override
    public double evalAsDouble(ExpressionEvaluator<?> evaluator) {
        return value;
    }

    @Override
    public boolean evalAsBoolean(ExpressionEvaluator<?> evaluator) {
        return value != 0;
    }

    public double evalAsDouble() {
        return value;
    }

    public boolean evalAsBoolean() {
        return value != 0;
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) {
        return value;
    }
}
