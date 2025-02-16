package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.core.context.IContext;
import net.minecraft.world.entity.LivingEntity;

public abstract class LivingEntityFunction extends ContextFunction<LivingEntity> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LivingEntity;
    }
}
