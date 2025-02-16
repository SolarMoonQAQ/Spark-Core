package cn.solarmoon.spark_core.molang.core.variable.entity;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import net.minecraft.world.entity.LivingEntity;

public class LivingEntityVariable extends LambdaVariable<LivingEntity> {
    public LivingEntityVariable(IValueEvaluator<?, IContext<LivingEntity>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LivingEntity;
    }
}
