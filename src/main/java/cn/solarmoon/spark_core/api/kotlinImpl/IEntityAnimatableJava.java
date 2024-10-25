package cn.solarmoon.spark_core.api.kotlinImpl;

import cn.solarmoon.spark_core.api.animation.anim.play.AnimData;
import cn.solarmoon.spark_core.api.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.api.animation.anim.part.Animation;
import cn.solarmoon.spark_core.api.animation.sync.AnimNetData;
import cn.solarmoon.spark_core.api.phys.collision.FreeCollisionBox;
import cn.solarmoon.spark_core.registry.common.SparkAttachments;
import kotlin.jvm.functions.Function0;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 可恶我竟然不知道怎么把kotlin接口用到java mixin里
public interface IEntityAnimatableJava<T extends Entity> extends IEntityAnimatable<T> {

    @Override
    default AnimData getAnimData() {
        return getAnimatable().getData(SparkAttachments.getANIM_DATA());
    }

    @Override
    @NotNull
    default List<@NotNull String> getPassableBones() {
        return List.of();
    }

    @Override
    default void setAnimData(AnimData value) {
        getAnimatable().setData(SparkAttachments.getANIM_DATA(), value);
    }

    @Override
    default @NotNull List<@NotNull String> getTurnBodyAnims() {
        return List.of();
    }

    @Override
    default void syncAnimDataToClient() {
        PacketDistributor.sendToAllPlayers(new AnimNetData(getAnimatable().getId(), getAnimData()));
    }

    @Override
    default Vector3f getBonePivot(String name, float partialTick) {
        var ma = getPositionMatrix(partialTick);
        var bone = getAnimData().getModel().getBone(name);
        bone.applyTransformWithParents(getAnimData().getPlayData(), ma, getExtraTransform(partialTick), partialTick);
        var pivot = bone.getPivot().toVector3f();
        return ma.transformPosition(pivot);
    }

    @Override
    default @NotNull Matrix4f getPositionMatrix(float partialTick) {
        return new Matrix4f().translate(getAnimatable().getPosition(partialTick).toVector3f()).rotateY((float) (Math.PI - Math.toRadians(getAnimatable().getPreciseBodyRotation(partialTick))));
    }

    @Override
    @NotNull
    default Map<String, Matrix4f> getExtraTransform(float partialTick) {
        var pitch = -Math.toRadians(getAnimatable().getViewXRot(partialTick));
        var yaw = -Math.toRadians(getAnimatable().getViewYRot(partialTick)) + Math.toRadians(getAnimatable().getPreciseBodyRotation(partialTick));
        return Map.of("head", new Matrix4f().rotateZYX(0f, (float) yaw, (float) pitch));
    }

    @Override
    default @NotNull Matrix4f getBoneMatrix(@NotNull String name, float partialTick) {
        var ma = getPositionMatrix(partialTick);
        var bone = getAnimData().getModel().getBone(name);
        bone.applyTransformWithParents(getAnimData().getPlayData(), ma, getExtraTransform(partialTick), partialTick);
        return ma;
    }

    @Override
    default @NotNull FreeCollisionBox createCollisionBoxBoundToBone(@NotNull String boneName, @NotNull Vector3f size, @NotNull Vector3f offset) {
        var box = new FreeCollisionBox(getBonePivot(boneName, 0f), size, new Quaternionf());
        box.getRotation().setFromUnnormalized(getBoneMatrix(boneName, 0f));
        box.offsetCenter(offset);
        return box;
    }

}
