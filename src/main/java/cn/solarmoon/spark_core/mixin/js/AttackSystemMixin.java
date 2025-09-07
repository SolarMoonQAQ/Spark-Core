package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.entity.attack.AttackSystem;
import cn.solarmoon.spark_core.js.extension.JSAttackSystem;
import cn.solarmoon.spark_core.lua.extensions.LuaAttackSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AttackSystem.class)
public class AttackSystemMixin implements LuaAttackSystem {
} 