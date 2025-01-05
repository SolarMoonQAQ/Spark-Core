package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.auto_anim.EntityAutoAnim;
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
            // 播放指定动画时将身体转到目视方向
            if (animatable.getAnimData().getPlayData().getMixedAnims().stream().anyMatch(MixedAnimation::getShouldTurnBody)
            || animatable.getAutoAnims().stream().anyMatch(i -> i instanceof EntityAutoAnim e && e.getShouldTurnBody() && e.isPlaying(0, wi -> true))) {
                tickHeadTurn(getYRot(), 100);
            }
        }
    }

}
