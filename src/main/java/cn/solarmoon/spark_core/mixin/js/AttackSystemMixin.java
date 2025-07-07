package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.entity.attack.AttackSystem;
import cn.solarmoon.spark_core.js.extension.JSAttackSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AttackSystem.class)
public class AttackSystemMixin implements JSAttackSystem {
} 