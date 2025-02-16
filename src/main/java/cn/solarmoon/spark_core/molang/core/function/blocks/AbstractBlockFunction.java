package cn.solarmoon.spark_core.molang.core.function.blocks;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.level.block.state.BlockBehaviour;

public abstract class AbstractBlockFunction extends ContextFunction<BlockBehaviour> {
    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof BlockBehaviour;
    }
}
