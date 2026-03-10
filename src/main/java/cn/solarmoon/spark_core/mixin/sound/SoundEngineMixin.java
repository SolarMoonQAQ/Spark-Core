package cn.solarmoon.spark_core.mixin.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundEngineMixin;
import cn.solarmoon.spark_core.sound.ClientSpreadingSoundPlayer;
import cn.solarmoon.spark_core.sound.SoundSourcePoint;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
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

    /**
     * 存放所有正在扩散的音效实例(正在播放的存放于SoundEngine中的tickingSounds中)
     */
    @Unique
    private final List<SpreadingSoundInstance> spark_core$spreadingSounds = new ArrayList<>();
    @Unique
    private final CopyOnWriteArrayList<SpreadingSoundInstance> spark_core$spreadingSoundsBuffer = new CopyOnWriteArrayList<>();

    @Shadow
    public void play(SoundInstance soundInstance) {
    }


    @Inject(method = "tickNonPaused", at = @At("RETURN"))
    private void spark_core$tickSpreadingSounds(CallbackInfo ci) {
        Vec3 listenerPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        this.spark_core$spreadingSounds.addAll(this.spark_core$spreadingSoundsBuffer);
        this.spark_core$spreadingSoundsBuffer.clear();
        Iterator<SpreadingSoundInstance> iterator = this.spark_core$spreadingSounds.iterator();
        while (iterator.hasNext()) {
            SpreadingSoundInstance instance = iterator.next();

            // 移除已停止的实例
            if (instance.isStopped()) {
                iterator.remove();
                continue;
            }
            // 先检查收听者是否在声音传播范围内
            if (!instance.isListenerInRange(listenerPos) || instance.isStopped()) {
                if (!instance.isPlaying) {
                    // 已开始播放的声音实例存在于SoundEngine的tickingSounds中，由SoundEngine负责更新
                    instance.tick();
                }
                // 不在范围内，跳过详细的波面检查
                continue;
            }

            // 检查所有已到达的声源点 TODO:检测收听者和相邻两点连线段的距离，或许更加准确？
            List<SoundSourcePoint> reachedPoints = instance.getReachedSoundPoints(listenerPos);
            if (!reachedPoints.isEmpty()) {
                for (SoundSourcePoint point : reachedPoints) {
                    instance.applySoundPoint(point);// 将抵达的波面的音效历史数据应用到声音实例

                    // 如果是第一个到达的波面且实例尚未播放，则开始播放
                    if (!instance.isPlaying && point == reachedPoints.getFirst()) {
                        //预初始化音调音量
                        instance.setVolume(spark_core$calculateVolume(instance));
                        instance.setPitch(spark_core$calculatePitch(instance));
                        this.play(instance);
                        instance.isPlaying = true;
                    }
                }
                instance.updateAABB();
            }

            if (instance.isPlaying && instance.soundPoints.isEmpty()) {
                // 所有波面都已处理完毕，可以安全移除
                iterator.remove();
            }
        }
        // 协调器清理
        if (Minecraft.getInstance().level != null) {
            ClientSpreadingSoundPlayer.cleanupStoppedInstances();
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
        this.spark_core$spreadingSoundsBuffer.add(sound);
    }

    @Unique
    public float spark_core$calculateVolume(SpreadingSoundInstance sound) {
        Vec3 sourcePos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        float distance = (float) listener.getTransform().position().distanceTo(sourcePos);
        float volume = sound.getVolume();
        //平方衰减
        // Square fall-off
        float rate = 1f - Math.min(distance / sound.getMaxRange(), 1f);
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
        Vec3 sourceSpeed = sound.getSpeed();
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
