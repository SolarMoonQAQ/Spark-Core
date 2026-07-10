package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.EntityPatch;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 画、物品展示框等依附于方块的装饰实体不需要默认物理刚体代理。
 */
@Mixin(BlockAttachedEntity.class)
public class BlockAttachedEntityMixin implements EntityPatch {

    @Override
    public boolean shouldCreateDefaultPhysicsBody() {
        return false;
    }
}
