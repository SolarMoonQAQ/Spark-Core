package cn.solarmoon.spark_core.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface ISpreadingSoundPlayer {
    /**
     * 通过网络包播放声音，不应被手动调用
     */
    void playSpreadingSoundFromPacket(Level level, UUID uuid, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume, int fadeIn, int fadeOut);

    /**
     * 在定点播放可传播的声音
     * @return UUID 用于标识该声音的唯一ID
     */
    UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume, int fadeIn, int fadeOut);

    /**
     * 创建并绑定可传播声音至音源，并播放
     * @return UUID 用于标识该声音的唯一ID
     */
    UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader ISoundSpreader, float maxRange, int fadeIn, int fadeOut);

    /**
     * 执行音效接力过渡
     * @param level 维度
     * @param oldSoundSource 旧声源标识（UUID）
     * @param newSoundEvent 新音效事件
     * @param soundType 声音类型
     * @param soundSpreader 动态声源
     * @param maxRange 最大范围
     * @param fadeIn 淡入时长
     * @param fadeOut 淡出时长
     * @return 新实例的UUID
     */
    UUID transitionSound(Level level, UUID oldSoundSource, SoundEvent newSoundEvent, SoundSource soundType,
                         ISoundSpreader soundSpreader, float maxRange, int fadeIn, int fadeOut);

    @Nullable
    SoundBuffer getSoundBuffer(ResourceLocation location);
}
