package cn.solarmoon.spark_core.molang.core.function.animatable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;

public abstract class AnimatableFunction extends ContextFunction<IAnimatable<?>> {
    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof IAnimatable<?>;
    }
}
