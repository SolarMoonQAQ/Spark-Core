package cn.solarmoon.spark_core.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.UUID;
import java.util.function.Supplier;

public class SpreadingSoundHelper {

    public static final ISpreadingSoundPlayer INSTANCE = init();

    private static ISpreadingSoundPlayer init() {
        //利用工厂方法隔离客户端和服务端的实现
        Supplier<ISpreadingSoundPlayer> spreadingSoundManager;
        if (FMLEnvironment.dist.isClient()) {
            spreadingSoundManager = ClientSpreadingSoundPlayer::new;
        } else {
            spreadingSoundManager = ServerSpreadingSoundPlayer::new;
        }
        return spreadingSoundManager.get();
    }

    /**
     * 通过网络包播放声音，不应被手动调用
     */
    public static void playSpreadingSoundFromPacket(Level level, UUID uuid, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut){
        INSTANCE.playSpreadingSoundFromPacket(level, uuid, soundEvent, soundType, position, speed, pitch, volume, fadeIn, fadeOut);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于定点播放的声音，双端可用
     *
     * @param level      播放声音的维度
     * @param soundEvent 音效事件，包含音效注册名
     * @param soundType  声音类型，方块，环境等
     * @param position   声音位置
     * @param speed      声音发出时的速度
     * @param pitch      声音的音高
     * @param volume     声音的音量
     * @param fadeIn     声音的淡入时间
     * @param fadeOut    声音的淡出时间
     * @return UUID 用于标识该声音的唯一ID
     */
    public static UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut) {
        return INSTANCE.playSpreadingSound(level, soundEvent, soundType, position, speed, pitch, volume, fadeIn, fadeOut);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于定点播放的声音，双端可用
     *
     * @param level      播放声音的维度
     * @param soundEvent 音效事件，包含音效注册名
     * @param soundType  声音类型，方块，环境等
     * @param position   声音位置
     * @param speed      声音发出时的速度
     * @param pitch      声音的音高
     * @param volume     声音的音量
     * @return UUID 用于标识该声音的唯一ID
     */
    public static UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume) {
        return INSTANCE.playSpreadingSound(level, soundEvent, soundType, position, speed, pitch, volume, 0, 0);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于持续播放且移动的声音，仅在客户端调用时有效
     *
     * @param level         播放声音的维度
     * @param soundEvent    声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType     声音类型，方块，环境等
     * @param soundSpreader 声源，音效会通过接口提供的方法更新其位置
     * @param fadeIn        声音的淡入时间
     * @param fadeOut       声音的淡出时间
     * @return UUID 用于标识该声音的唯一ID
     */
    public static UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader soundSpreader, int fadeIn, int fadeOut) {
        return INSTANCE.playSpreadingSound(level, soundEvent, soundType, soundSpreader, fadeIn, fadeOut);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于持续播放且移动的声音，仅在客户端调用时有效
     *
     * @param level         播放声音的维度
     * @param soundEvent    声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType     声音类型，方块，环境等
     * @param soundSpreader 声源，音效会通过接口提供的方法更新其位置
     * @return UUID 用于标识该声音的唯一ID
     */
    public static UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader soundSpreader) {
        return INSTANCE.playSpreadingSound(level, soundEvent, soundType, soundSpreader, 0, 0);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效并将其与现有的音效交叉淡入淡出(CrossFadeIn/Out)，适用引擎等音效会发生变化的音源，仅在客户端调用时有效
     *
     * @param level          播放声音的维度
     * @param oldSoundSource 旧声源标识（UUID）
     * @param newSoundEvent  新音效事件
     * @param soundType      声音类型
     * @param soundSpreader  动态声源
     * @param fadeIn         淡入时长
     * @param fadeOut        淡出时长
     * @return 新实例的UUID
     */
    public static UUID transitionSound(Level level, UUID oldSoundSource, SoundEvent newSoundEvent, SoundSource soundType,
                                       ISoundSpreader soundSpreader, int fadeIn, int fadeOut) {
        return INSTANCE.transitionSound(level, oldSoundSource, newSoundEvent, soundType, soundSpreader, fadeIn, fadeOut);
    }

    public static SoundBuffer getSoundBuffer(ResourceLocation location) {
        return INSTANCE.getSoundBuffer(location);
    }
}
