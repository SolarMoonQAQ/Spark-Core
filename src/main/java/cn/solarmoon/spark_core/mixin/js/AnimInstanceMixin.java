package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance;
import cn.solarmoon.spark_core.js.extensions.JSAnimInstance;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AnimInstance.class)
public class AnimInstanceMixin implements JSAnimInstance {
}
