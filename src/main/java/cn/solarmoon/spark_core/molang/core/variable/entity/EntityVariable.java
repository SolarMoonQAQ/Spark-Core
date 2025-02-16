package cn.solarmoon.spark_core.molang.core.variable.entity;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.entity.Entity;

public class EntityVariable extends LambdaVariable<Entity> {
    public EntityVariable(IValueEvaluator<?, IAnimatable<Entity>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof Entity;
    }
}
