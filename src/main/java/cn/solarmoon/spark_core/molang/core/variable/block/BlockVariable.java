package cn.solarmoon.spark_core.molang.core.variable.block;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import net.minecraft.world.level.block.Block;

public class BlockVariable extends LambdaVariable<Block> {
    public BlockVariable(IValueEvaluator<?, IAnimatable<Block>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof Block;
    }
}
