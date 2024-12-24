package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.auto_anim.AutoAnim;
import cn.solarmoon.spark_core.animation.anim.auto_anim.AutoAnimRegisterEvent;
import cn.solarmoon.spark_core.animation.anim.play.AnimData;
import cn.solarmoon.spark_core.entity.state.EntityStateHelper;
import cn.solarmoon.spark_core.phys.SparkMathKt;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import cn.solarmoon.spark_core.registry.common.SparkAttachments;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Lazy;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.ode4j.ode.DBody;
import org.ode4j.ode.OdeHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMixin extends AttachmentHolder {

    @Shadow public abstract Level level();

    @Shadow public abstract int getId();

    @Shadow public abstract EntityType<?> getType();

    @Shadow @Nullable public abstract <T> T setData(AttachmentType<T> type, T data);

    private static final EntityDataAccessor<Byte> DATA_STATE_FLAGS_ID = EntityStateHelper.getDATA_STATE_FLAGS_ID().getValue();
    private static final EntityDataAccessor<Float> DATA_STATE_SPEED = EntityStateHelper.getDATA_STATE_SPEED().getValue();

    private Entity entity = (Entity) (Object) this;
    private final Set<Lazy<AutoAnim<?>>> allAutoAnims = new LinkedHashSet<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(EntityType entityType, Level level, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable) {
            setData(SparkAttachments.getANIM_DATA(), AnimData.of(entity));
            NeoForge.EVENT_BUS.post(new AutoAnimRegisterEvent.Entity(animatable, entity, allAutoAnims));
        }
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V"))
    private void init2(EntityType entityType, Level level, CallbackInfo ci, @Local SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE_FLAGS_ID, (byte) 0);
        builder.define(DATA_STATE_SPEED, 0f);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable) {
            allAutoAnims.forEach(i -> {
                if (!i.isInitialized()) {
                    animatable.getAutoAnims().add(i.getValue());
                }
            });
        }
    }

}
