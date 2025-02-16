package cn.solarmoon.spark_core.molang.core.value;

import cn.solarmoon.spark_core.molang.engine.parser.ast.Expression;
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;

import java.util.List;

public class MolangValue implements IValue {
    private final Expression[] expressions;

    public MolangValue(List<Expression> expressions) {
        this.expressions = expressions.toArray(new Expression[0]);
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) throws Exception {
        Object lastResult = 0d;

        for (Expression expression : expressions) {
            lastResult = evaluator.eval(expression);
            Object returnValue = evaluator.popReturnValue();
            if (returnValue != null) {
                lastResult = returnValue;
                break;
            }
        }

        return lastResult;
    }
}
