package cn.solarmoon.spark_core.mixin.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundEngineMixin;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(value = SoundEngine.class)
public abstract class SoundEngineMixin implements ISoundEngineMixin {
    @Shadow
    @Final
    private Listener listener;

    @Shadow
    @Final
    private SoundBufferLibrary soundBuffers;

    @Unique
    private final List<SpreadingSoundInstance> spark_core$spreadingSounds = new CopyOnWriteArrayList<>();

    @Shadow
    public void play(SoundInstance soundInstance) {
    }


    @Inject(method = "tickNonPaused", at = @At("RETURN"))
    private void spark_core$tickSpreadingSounds(CallbackInfo ci) {
        Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (SpreadingSoundInstance instance : this.spark_core$spreadingSounds) {
            instance.tick();
            boolean shouldPlay = false;

            for (Vec3 key : instance.spreadDistances.keySet()) {
                float spreadDistance = instance.spreadDistances.get(key);
                double distance = pos.distanceToSqr(key);
                if (distance <= spreadDistance * spreadDistance) {
                    shouldPlay = true;
                    break;
                }
            }
            if (shouldPlay) {
                instance.setVolume(spark_core$calculateVolume(instance));
                instance.setPitch(spark_core$calculatePitch(instance));
                this.play(instance);
                instance.isPlaying = true;
                this.spark_core$spreadingSounds.remove(instance);
            }
        }
    }

    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void calculatePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof SpreadingSoundInstance instance) {
            float dopplerFactor = spark_core$calculatePitch(instance);
            cir.setReturnValue(Math.clamp(dopplerFactor, 0.25f, 4f));//限制多普勒因子的大小以防止极端音效
        }
    }

    @Inject(method = "calculateVolume*", at = @At("RETURN"), cancellable = true)
    private void calculateVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof SpreadingSoundInstance instance) {
            cir.setReturnValue(Math.clamp(spark_core$calculateVolume(instance), 0.0f, 1.0f));
        }
    }

    @Override
    public SoundBuffer spark_core$getSoundBuffer(ResourceLocation location) {
        var sound = soundBuffers.getCompleteBuffer(location);
        try {
            return sound.getNow(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    public void spark_core$queueSpreadingSound(SpreadingSoundInstance sound) {
        this.spark_core$spreadingSounds.add(sound);
    }

    @Unique
    public float spark_core$calculateVolume(SpreadingSoundInstance sound) {
        Vec3 sourcePos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        float distance = (float) listener.getTransform().position().distanceTo(sourcePos);
        float range = sound.getRange(sourcePos);
        float volume = sound.getVolume();
        //平方衰减
        // Square fall-off
        float rate = 1f - Math.min(distance / range, 1f);
        return volume * rate * rate;
    }

    @Unique
    public float spark_core$calculatePitch(SpreadingSoundInstance sound) {
        Vec3 listenerSpeed;
        if (Minecraft.getInstance().getCameraEntity() instanceof Entity entity)
            listenerSpeed = entity.getDeltaMovement().scale(20);
        else
            listenerSpeed = Vec3.ZERO;
        Vec3 sourcePos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        Vec3 sourceSpeed = sound.getSpeed(sourcePos);
        Vec3 toListener = listener.getTransform().position().subtract(sourcePos).normalize();

        // 计算相对速度在方向上的投影
        // Calculate the projection of the relative speed on the direction
        float relativeSpeed = (float) sourceSpeed.dot(toListener)
                - (float) listenerSpeed.dot(toListener);

        // 多普勒因子
        // Doppler factor
        return (1.0f + relativeSpeed / SpreadingSoundInstance.getSoundSpeed(
                sourcePos.scale(0.5)
                        .add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().scale(0.5)),
                Minecraft.getInstance().level)) * sound.getPitch();
    }
}
