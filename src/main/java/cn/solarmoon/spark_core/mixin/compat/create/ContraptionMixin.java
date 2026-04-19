package cn.solarmoon.spark_core.mixin.compat.create;

import cn.solarmoon.spark_core.compat.create.CreateContraptionPhysicsApplier;
import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create 装置碰撞体失效事件桥接。
 * <p>
 * 设计说明：
 * - 仅负责把 Create 的“碰撞器失效”信号转发给 Spark 兼容层；
 * - 兼容层在下一次物理同步周期执行真实重建，避免在当前调用栈做重型操作。
 */
@Mixin(Contraption.class)
public class ContraptionMixin {

    /**
     * 在 Create 标记碰撞器失效时，将对应宿主碰撞体标记为脏。
     */
    @Inject(method = "invalidateColliders", at = @At("HEAD"))
    private void spark_core$markCreateContraptionDirty(CallbackInfo ci) {
        CreateContraptionPhysicsApplier.INSTANCE.markDirty((Contraption) (Object) this);
    }
}

