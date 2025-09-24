package cn.solarmoon.spark_core.mixin.animation.humanoid.first_person;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.compat.first_person_model.FirstPersonModelCompat;
import cn.solarmoon.spark_core.compat.real_camera.RealCameraCompat;
import cn.solarmoon.spark_core.event.CameraFollowHeadEvent;
import cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    private final Camera camera = (Camera) (Object) this;

    @Shadow public abstract Vec3 getPosition();

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow private float eyeHeightOld;

    @Shadow private float eyeHeight;

    @Shadow protected abstract void move(float d, float e, float f);

    @Shadow protected abstract void setRotation(float f, float g);

    @Shadow protected abstract float getMaxZoom(float d);

    @Shadow private float xRot;

    @Shadow private float yRot;

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void setup(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable && animatable.getModelController().getOriginModel().hasBone("head")) {
            var event = NeoForge.EVENT_BUS.post(new CameraFollowHeadEvent(entity, camera, RealCameraCompat.INSTANCE.isActive() || FirstPersonModelCompat.INSTANCE.isActive()));
            if (event.isEnabled()) {
                var model = animatable.getModelController().getModel();
                if (model == null) return;
                var pose = model.getPose().getBonePose("head");
                var pos = pose.getWorldBonePivot(Vec3.ZERO, partialTick);
                var pos2 = pose.getWorldBonePivot(new Vec3(0.0, Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight) - (pos.y - Mth.lerp(partialTick, entity.yo, entity.getY())), 0.0), partialTick);
                setPosition(pos.x, pos2.y, pos.z);
            }
        }
    }

}
