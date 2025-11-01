package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.pack.modules.SoundModule;
import cn.solarmoon.spark_core.registry.common.SparkSounds;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;

/**
 * 参考自TACZ的GunSoundInstance
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class CustomSoundInstance extends SpreadingSoundInstance {
    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例</p>
     *
     * @param soundType 声音类型，方块，环境等
     * @param location 声音文件路径
     * @param position 声音位置
     * @param speed 声音发出时的速度
     * @param range 声音的最大距离
     * @param pitch 声音的音高
     * @param volume 声音的音量
     */
    public CustomSoundInstance(SoundSource soundType, ResourceLocation location, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        super(SparkSounds.getCUSTOM_SOUND().get(), soundType, location, position, speed, range, pitch, volume);
    }

    /**
     * <p>针对持续发出声音的声源的构造函数，用于创建位置速度音高等时刻改变声源的音效实例</p>
     *
     * @param soundType 声音类型，方块，环境等
     * @param location 声音文件路径
     * @param soundSpreader 声音的位置、速度、音高等时刻变化的接口
     */
    public CustomSoundInstance(SoundSource soundType, ResourceLocation location, ISoundSpreader soundSpreader) {
        super(SparkSounds.getCUSTOM_SOUND().get(), soundType, location, soundSpreader);
    }

    @Nullable
    public SoundBuffer getSoundBuffer() {
        SoundData soundData = SoundModule.getSound(this.name);
        if (soundData == null) {
            return SpreadingSoundHelper.INSTANCE.getSoundBuffer(this.name);
        }
        AudioFormat rawFormat = soundData.audioFormat();
        if (rawFormat.getChannels() > 1) {
            AudioFormat monoFormat = new AudioFormat(rawFormat.getEncoding(), rawFormat.getSampleRate(), rawFormat.getSampleSizeInBits(), 1, rawFormat.getFrameSize(), rawFormat.getFrameRate(), rawFormat.isBigEndian(), rawFormat.properties());
            return new SoundBuffer(soundData.byteBuffer(), monoFormat);
        }
        return new SoundBuffer(soundData.byteBuffer(), soundData.audioFormat());
    }

    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (event.getSound() instanceof CustomSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
            }
        }
    }
}
