package cn.solarmoon.spark_core.mixin.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundEngineMixin;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
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

import java.util.Iterator;
import java.util.List;

@Mixin(value = SoundEngine.class)
public abstract class SoundEngineMixin implements ISoundEngineMixin {
    @Shadow
    @Final
    private Listener listener;

    @Unique
    private final List<SpreadingSoundInstance> machine_Max$spreadingSounds = Lists.newArrayList();

    @Shadow
    public void play(SoundInstance soundInstance) {
    }


    @Inject(method = "tickNonPaused", at = @At("RETURN"))
    private void machine_Max$tickSpreadingSounds(CallbackInfo ci) {
        Iterator<SpreadingSoundInstance> iterator = this.machine_Max$spreadingSounds.iterator();
        Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // 遍历正在传播的声音实例
        // Traverse the spreading sound instances
        while (iterator.hasNext()) {
            SpreadingSoundInstance instance = iterator.next();
            // 调用 tick 方法更新声音实例
            // Call the tick method to update the sound instance
            instance.tick();
            boolean shouldPlay = false;
            // 遍历声音实例的 spreadDistances 键集，每个键代表一个声音的传播中心点
            // Traverse the keys of the spreadDistances key set in the sound instance, each key represents a sound propagation center point
            for (Vec3 key : instance.spreadDistances.keySet()) {
                float spreadDistance = instance.spreadDistances.get(key);
                double distance = pos.distanceToSqr(key);
                // 如果相机位置到中心点的距离平方小于或等于传播距离的平方，说明相机在声音传播范围内
                // If the squared distance between the camera position and the center point is less than or equal to the squared propagation distance, the camera is within the sound propagation range
                if (distance <= spreadDistance * spreadDistance) {
                    shouldPlay = true;
                    break;
                }
            }
            if (shouldPlay) {
                instance.setVolume(machine_Max$calculateVolume(instance));
                instance.setPitch(machine_Max$calculatePitch(instance));
                this.play(instance);
                instance.isPlaying = true;
                // 从集合中移除已经播放的声音实例
                // Remove the played sound instance from the collection
                iterator.remove();
            }
        }
    }

    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void calculatePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof SpreadingSoundInstance instance) {
            float dopplerFactor = machine_Max$calculatePitch(instance);
            cir.setReturnValue(Math.clamp(dopplerFactor, 0.25f, 4f));//限制多普勒因子的大小以防止极端音效
        }
    }

    @Inject(method = "calculateVolume*", at = @At("RETURN"), cancellable = true)
    private void calculateVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof SpreadingSoundInstance instance) {
            cir.setReturnValue(Math.clamp(machine_Max$calculateVolume(instance), 0.0f, 1.0f));
        }
    }

    @Unique
    public void machine_Max$queueSpreadingSound(SpreadingSoundInstance sound) {
        this.machine_Max$spreadingSounds.add(sound);
    }

    @Unique
    public float machine_Max$calculateVolume(SpreadingSoundInstance sound) {
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
    public float machine_Max$calculatePitch(SpreadingSoundInstance sound) {
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
