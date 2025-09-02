package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.animation.anim.play.KeyframeRange;
import cn.solarmoon.spark_core.js2.extension.JSKeyframeRange;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyframeRange.class)
public class KeyframeRangeMixin implements JSKeyframeRange {
}
