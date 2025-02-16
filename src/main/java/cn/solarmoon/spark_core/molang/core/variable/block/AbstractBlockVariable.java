package cn.solarmoon.spark_core.molang.core.variable.block;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class AbstractBlockVariable extends LambdaVariable<BlockBehaviour> {
    public AbstractBlockVariable(IValueEvaluator<?, IAnimatable<BlockBehaviour>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof BlockBehaviour;
    }
}
