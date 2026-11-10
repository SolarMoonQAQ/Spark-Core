package cn.solarmoon.spark_core.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public interface ISpreadingSoundPlayer {
    /**
     * 通过网络包播放声音，不应被手动调用
     */
    void playSpreadingSoundFromPacket(Level level, UUID uuid, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut, boolean loop);

    /**
     * 在定点播放可传播的声音
     * @return UUID 用于标识该声音的唯一ID
     */
    UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut, boolean loop);

    /**
     * 创建并绑定可传播声音至音源，并播放
     * @return UUID 用于标识该声音的唯一ID
     */
    UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader ISoundSpreader, int fadeIn, int fadeOut, boolean loop);

    /**
     * 执行音效接力过渡
     * @param level 维度
     * @param oldSoundSource 旧声源标识（UUID）
     * @param newSoundEvent 新音效事件
     * @param soundType 声音类型
     * @param soundSpreader 动态声源
     * @param fadeIn 淡入时长
     * @param fadeOut 淡出时长
     * @param loop 是否循环
     * @return 新实例的UUID
     */
    UUID transitionSound(Level level, UUID oldSoundSource, SoundEvent newSoundEvent, SoundSource soundType,
                         ISoundSpreader soundSpreader, int fadeIn, int fadeOut, boolean loop);

    /**
     * 开始淡出指定声源
     */
    void fadeSound(Level level, UUID soundSource);

    /**
     * 立即停止指定声源
     */
    void stopSound(Level level, UUID soundSource);

    /** 查询当前客户端世界可用的 FMOD bank。服务端默认返回 false。 */
    default boolean isFmodBankLoaded(Level level, ResourceLocation bankId) {
        return false;
    }

    /** 在听者原位播放 FMOD 事件。服务端或无法启动时返回 null。 */
    @Nullable
    default UUID tryPlayFmodSound(Level level, ResourceLocation bankId, String eventPath,
                                 SoundSource category, Map<String, Float> initialParams) {
        return null;
    }

    /** 更新 FMOD 实例的局部参数。返回本次更新是否被接受。 */
    default boolean setFmodParameter(Level level, @Nullable UUID uuid, String name, float value) {
        return false;
    }

    /** 按 Studio 事件的停止过渡淡出。服务端或无对应实例时返回 false。 */
    default boolean fadeFmodSound(Level level, @Nullable UUID uuid) {
        return false;
    }

    /** 立即停止 FMOD 实例。服务端或无对应实例时返回 false。 */
    default boolean stopFmodSound(Level level, @Nullable UUID uuid) {
        return false;
    }

    @Nullable
    SoundBuffer getSoundBuffer(ResourceLocation location);
}
