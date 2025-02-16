package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.entity.projectile.Arrow;

public abstract class ArrowEntityFunction extends ContextFunction<Arrow> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Arrow;
    }
}
