package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.entity.attack.CollisionAttackSystem;
import cn.solarmoon.spark_core.js.extensions.JSAttackSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CollisionAttackSystem.class)
public class AttackSystemMixin implements JSAttackSystem {
} 