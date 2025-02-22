package cn.solarmoon.spark_core.molang.core.variable.entity;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import net.minecraft.world.entity.TamableAnimal;

public class TamableEntityVariable extends LambdaVariable<TamableAnimal> {
    public TamableEntityVariable(IValueEvaluator<?, IAnimatable<TamableAnimal>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof TamableAnimal;
    }
}
