package cn.solarmoon.spark_core.molang.core.variable.entity;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.entity.Mob;

public class MobEntityVariable extends LambdaVariable<Mob> {
    public MobEntityVariable(IValueEvaluator<?, IContext<Mob>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Mob;
    }
}
