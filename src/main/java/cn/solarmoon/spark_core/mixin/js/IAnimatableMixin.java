package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.lua.extensions.LuaAnimatable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IAnimatable.class)
public interface IAnimatableMixin extends LuaAnimatable {



}
