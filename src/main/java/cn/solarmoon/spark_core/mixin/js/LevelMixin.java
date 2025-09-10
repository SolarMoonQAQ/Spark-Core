package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.extensions.JSLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public class LevelMixin implements JSLevel {
}
