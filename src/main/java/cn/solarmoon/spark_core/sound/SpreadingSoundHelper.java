package cn.solarmoon.spark_core.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

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
     * 播放带音速延迟和多普勒效果的扩散音效，适用于定点播放的声音，双端可用
     * @param level 播放声音的维度
     * @param soundEvent 音效事件，包含音效注册名
     * @param soundType 声音类型，方块，环境等
     * @param position 声音位置
     * @param speed 声音发出时的速度
     * @param range 声音的最大距离
     * @param pitch 声音的音高
     * @param volume 声音的音量
     */
    public static void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume){
        INSTANCE.playSpreadingSound(level, soundEvent, soundType, position, speed, range, pitch, volume);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于持续播放且移动的声音，仅在客户端调用时有效
     * @param level 播放声音的维度
     * @param soundEvent 声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType 声音类型，方块，环境等
     * @param soundSpreader 声源，音效会通过接口提供的方法更新其位置
     */
    public static void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader soundSpreader){
        INSTANCE.playSpreadingSound(level, soundEvent, soundType, soundSpreader);
    }

    public static SoundBuffer getSoundBuffer(ResourceLocation location){
        return INSTANCE.getSoundBuffer(location);
    }
}
