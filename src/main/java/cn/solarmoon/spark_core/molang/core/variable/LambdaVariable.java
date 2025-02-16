package cn.solarmoon.spark_core.molang.core.variable;


import cn.solarmoon.spark_core.animation.IAnimatable;

public class LambdaVariable<TTarget> extends ContextVariable<TTarget> {
    private final IValueEvaluator<?, IAnimatable<TTarget>> evaluator;

    public LambdaVariable(IValueEvaluator<?, IAnimatable<TTarget>> evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public final Object evaluate(IAnimatable<TTarget> entityContext) {
        return evaluator.eval(entityContext);
    }
}
