package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.SparkCore; // Import SparkCore for logging
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.ik.caliko.IKApplier; // Import IKApplier
import cn.solarmoon.spark_core.ik.caliko.IKResolver; // Import IKResolver
import cn.solarmoon.spark_core.ik.component.IKComponent; // Import IKComponent
import cn.solarmoon.spark_core.ik.component.IKHost; // Import IKHost
import cn.solarmoon.spark_core.ik.component.IKManager; // Import IKManager
import cn.solarmoon.spark_core.physics.level.PhysicsLevel; // Import PhysicsLevel
import com.jme3.math.Vector3f; // Import Vector3f
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
