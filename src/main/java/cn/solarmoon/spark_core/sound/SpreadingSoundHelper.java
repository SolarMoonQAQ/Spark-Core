package cn.solarmoon.spark_core.sound;

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
     * 播放带音速延迟和多普勒效果的扩散音效，双端可用
     * @param soundEvent
     * @param soundType
     * @param position
     * @param speed
     * @param range
     * @param pitch
     * @param volume
     */
    public static void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume){
        INSTANCE.playSpreadingSound(level, soundEvent, soundType, position, speed, range, pitch, volume);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，仅在客户端调用时有效
     * @param soundEvent
     * @param soundType
     * @param ISpreadingSoundSource
     */
    public static void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource){
        INSTANCE.playSpreadingSound(level, soundEvent, soundType, ISpreadingSoundSource);
    }
}
