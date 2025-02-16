package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.client.player.AbstractClientPlayer;

public abstract class PlayerEntityFunction extends ContextFunction<AbstractClientPlayer> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractClientPlayer;
    }
}
