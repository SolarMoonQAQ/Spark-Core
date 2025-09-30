package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.entity.attack.AttackContext;
import cn.solarmoon.spark_core.entity.attack.CollisionAttackSystem;
import cn.solarmoon.spark_core.js.extensions.JSAttackContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AttackContext.class)
public class AttackContextMixin implements JSAttackContext {
} 