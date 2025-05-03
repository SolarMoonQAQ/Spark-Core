package cn.solarmoon.spark_core.mixin.animation;

// Import SparkCore for logging

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
// Import IKApplier
// Import IKResolver
// Import IKComponent
// Import IEntityAnimatable
// Import IKManager
// Import PhysicsLevel
// Import Vector3f
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow protected abstract float tickHeadTurn(float yRot, float animStep);

    private LivingEntity entity = (LivingEntity) (Object) this;

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable) {
            // --- Existing Animation Logic ---
            // 播放指定动画时将身体转到目视方向
            var anim = animatable.getAnimController().getPlayingAnim();
            if (anim != null && anim.getShouldTurnBody()) {
                tickHeadTurn(getYRot(), 100);
            }
        }
    }

}
