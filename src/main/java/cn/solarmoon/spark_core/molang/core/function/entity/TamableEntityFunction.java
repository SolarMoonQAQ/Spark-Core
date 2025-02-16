package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.entity.TamableAnimal;

public abstract class TamableEntityFunction extends ContextFunction<TamableAnimal> {
    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof TamableAnimal;
    }
}
