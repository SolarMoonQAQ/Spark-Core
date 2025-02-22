package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.client.player.AbstractClientPlayer;

public abstract class PlayerEntityFunction extends ContextFunction<AbstractClientPlayer> {
    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof AbstractClientPlayer;
    }
}
