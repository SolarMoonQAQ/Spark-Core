package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundManagerMixin;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientSpreadingSoundPlayer implements ISpreadingSoundPlayer {

    public static final RandomSource RANDOM = RandomSource.create();

    private static final ConcurrentHashMap<UUID, SpreadingSoundInstance> activeInstances = new ConcurrentHashMap<>();

    public static void playSpreadingSound(SpreadingSoundInstance soundInstance) {
        ISoundManagerMixin soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        soundManager.spark_core$playSpreading(soundInstance);
        activeInstances.put(soundInstance.getUUID(), soundInstance);
    }

    @Override
    public void playSpreadingSoundFromPacket(Level level, UUID uuid, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut, boolean loop) {
        var sound = new SpreadingSoundInstance(soundEvent, soundType, position, speed, pitch, volume, fadeIn, fadeOut, loop);
        sound.setUUID(uuid);
        playSpreadingSound(sound);
    }

    public UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut, boolean loop) {
        var sound = new SpreadingSoundInstance(soundEvent, soundType, position, speed, pitch, volume, fadeIn, fadeOut, loop);
        playSpreadingSound(sound);
        return sound.getUUID();
    }

    public UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader ISoundSpreader, int fadeIn, int fadeOut, boolean loop) {
        var sound = new SpreadingSoundInstance(soundEvent, soundType, ISoundSpreader, fadeIn, fadeOut, loop);
        playSpreadingSound(sound);
        return sound.getUUID();
    }

    /**
     * 执行音效接力过渡
     * @param oldSoundSource 声源标识（UUID）
     * @param newSoundEvent 新音效事件
     * @param soundType 声音类型
     * @param soundSpreader 动态声源（可为null）
     * @param fadeIn 淡入时长
     * @param fadeOut 淡出时长
     * @param loop 是否循环
     * @return 新实例的UUID
     */
    public UUID transitionSound(Level level, UUID oldSoundSource, SoundEvent newSoundEvent, SoundSource soundType,
                                ISoundSpreader soundSpreader, int fadeIn, int fadeOut, boolean loop) {
        SpreadingSoundInstance oldInstance = activeInstances.get(oldSoundSource);

        // 创建新实例
        SpreadingSoundInstance newInstance = new SpreadingSoundInstance(newSoundEvent, soundType, soundSpreader, fadeIn, fadeOut, loop);

        // 如果存在旧实例，复制声源点并开始淡出
        if (oldInstance != null) {
            // 旧实例没有可用波面时，不覆盖新实例构造时创建的初始波面，避免新实例无法启动播放。
            if (!oldInstance.soundPoints.isEmpty()) {
                newInstance.copySoundPointsFrom(oldInstance);
            }
            oldInstance.startFadeOut();
        }

        // 新实例播放
        ClientSpreadingSoundPlayer.playSpreadingSound(newInstance);
        return newInstance.getUUID();
    }

    @Override
    public void fadeSound(Level level, UUID soundSource) {
        fadeSound(soundSource);
    }

    @Override
    public void stopSound(Level level, UUID soundSource) {
        stopSound(soundSource);
    }

    @Override
    public @Nullable SoundBuffer getSoundBuffer(ResourceLocation location) {
        ISoundManagerMixin soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        WeighedSoundEvents weighedSoundEvents = ((SoundManager)soundManager).getSoundEvent(location);
        if (weighedSoundEvents == null) {
            return null;
        }else {
            Sound sound = weighedSoundEvents.getSound(RANDOM);
            return soundManager.spark_core$getSoundBuffer(sound.getPath());
        }
    }

    /**
     * 移除完成的实例
     */
    public static void cleanupStoppedInstances() {
        activeInstances.entrySet().removeIf(entry -> !entry.getValue().shouldGenerateNewPoints() &&
                entry.getValue().isStopped() && entry.getValue().isFadedOut()
        );
    }

    /**
     * 开始淡出指定声源
     */
    public static void fadeSound(UUID soundSource) {
        SpreadingSoundInstance instance = activeInstances.get(soundSource);
        if (instance != null) {
            instance.startFadeOut();
        }
        activeInstances.remove(soundSource);
    }

    /**
     * 停止指定声源的播放
     */
    public static void stopSound(UUID soundSource) {
        SpreadingSoundInstance instance = activeInstances.get(soundSource);
        if (instance != null) {
            instance.stopImmediately();
        }
        activeInstances.remove(soundSource);
    }

    /**
     * 获取当前活跃实例
     */
    public static SpreadingSoundInstance getActiveInstance(UUID soundSource) {
        return activeInstances.get(soundSource);
    }
}
