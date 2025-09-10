package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.extensions.JSPreInput;
import cn.solarmoon.spark_core.preinput.PreInput;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PreInput.class)
public class PreInputMixin implements JSPreInput {
}
