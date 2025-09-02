package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js2.extension.JSEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class EntityMixin implements JSEntity {
}
