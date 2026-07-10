package cn.solarmoon.spark_core.mixin.compat.create;

import cn.solarmoon.spark_core.EntityPatch;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Create 装置实体禁用 SparkCore 默认刚体代理。
 * <p>
 * Create 装置由 {@link cn.solarmoon.spark_core.compat.create.CreateContraptionPhysicsApplier}
 * 自行管理物理刚体，因此不应再附加默认的 "body" 代理，避免碰撞体重复。
 */
@Mixin(AbstractContraptionEntity.class)
public class AbstractContraptionEntityMixin implements EntityPatch {

    @Override
    public boolean shouldCreateDefaultPhysicsBody() {
        return false;
    }
}
