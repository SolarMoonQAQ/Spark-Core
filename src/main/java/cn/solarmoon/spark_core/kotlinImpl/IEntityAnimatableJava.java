package cn.solarmoon.spark_core.kotlinImpl;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimData;
import cn.solarmoon.spark_core.animation.sync.AnimDataPayload;
import cn.solarmoon.spark_core.registry.common.SparkAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IEntityAnimatableJava<T extends Entity> extends IEntityAnimatable<T> {

    @Override
    default AnimData getAnimData() {
        return getAnimatable().getData(SparkAttachments.getANIM_DATA());
    }

    @Override
    default Level getLevel() {
        return getAnimatable().level();
    }

    @Override
    default void setAnimData(AnimData value) {
        getAnimatable().setData(SparkAttachments.getANIM_DATA(), value);
    }

    @Override
    default void syncAnimDataToClient(@Nullable ServerPlayer player) {
        var data = new AnimDataPayload(getAnimatable().getId(), getAnimData().copy());
        if (!getAnimatable().level().isClientSide) {
            if (player != null) {
                PacketDistributor.sendToPlayersNear(player.serverLevel(), player, player.getX(), player.getY(), player.getZ(), 512, data);
            }
            else PacketDistributor.sendToAllPlayers(data);
        }
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

}
