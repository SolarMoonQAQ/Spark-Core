package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.extensions.JSEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class EntityMixin implements JSEntity {
}
