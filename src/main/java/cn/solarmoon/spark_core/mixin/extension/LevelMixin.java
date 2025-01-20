package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.phys.thread.ILaterConsumerHolder;
import kotlin.Unit;
import kotlin.collections.ArrayDeque;
import kotlin.jvm.functions.Function0;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public class LevelMixin implements ILaterConsumerHolder {

    private final ArrayDeque<@NotNull Function0<@NotNull Unit>> consumers = new ArrayDeque<>();


    @Override
    public @NotNull ArrayDeque<@NotNull Function0<@NotNull Unit>> getConsumers() {
        return consumers;
    }

}
