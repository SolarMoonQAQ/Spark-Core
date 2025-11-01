package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.pack.modules.SoundModule;
import cn.solarmoon.spark_core.registry.common.SparkSounds;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 带音速传播(非延迟，而是波面抵达收听者)和多普勒效应的音效实例
 * 声音传播范围的实现参考自https://github.com/MCModderAnchor/TACZ
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SpreadingSoundInstance extends AbstractTickableSoundInstance {
    @Nullable
    public final ISoundSpreader ISoundSpreader;
    private final SoundEvent soundEvent;
    public final LinkedHashMap<Vec3, Float> spreadDistances = new LinkedHashMap<>(20);
    public final LinkedHashMap<Vec3, Vec3> speeds = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> ranges = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> pitches = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> volumes = new LinkedHashMap<>(20);
    public boolean isPlaying = false;

    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例</p>
     *
     * @param soundEvent 声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType 声音类型，方块，环境等
     * @param position 声音位置
     * @param speed 声音发出时的速度
     * @param range 声音的最大距离
     * @param pitch 声音的音高
     * @param volume 声音的音量
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        super(SparkSounds.getCUSTOM_SOUND().get(), soundType, SoundInstance.createUnseededRandom());
        this.ISoundSpreader = null;
        this.attenuation = Attenuation.NONE;
        this.soundEvent = soundEvent;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.pitch = pitch;
        this.volume = volume;
        this.spreadDistances.put(position, 0F);
        this.speeds.put(position, speed);
        this.ranges.put(position, range);
        this.pitches.put(position, pitch);
        this.volumes.put(position, volume);
    }

    /**
     * <p>针对持续发出声音的声源的构造函数，用于创建位置速度音高等时刻改变声源的音效实例</p>
     *
     * @param soundEvent 声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType 声音类型，方块，环境等
     * @param soundSpreader 声源，音效会通过接口提供的方法更新其位置
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, ISoundSpreader soundSpreader) {
        super(SparkSounds.getCUSTOM_SOUND().get(), soundType, SoundInstance.createUnseededRandom());
        this.ISoundSpreader = soundSpreader;
        this.attenuation = Attenuation.NONE;
        this.soundEvent = soundEvent;
        var position = soundSpreader.getPosition(soundEvent);
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.pitch = soundSpreader.getPitch(soundEvent);
        this.volume = soundSpreader.getVolume(soundEvent);
        this.spreadDistances.put(position, 0F);
        this.speeds.put(position, soundSpreader.getSpeed(soundEvent));
        this.ranges.put(position, soundSpreader.getRange(soundEvent));
        this.pitches.put(position, this.pitch);
        this.volumes.put(position, this.volume);
    }

    @Override
    public void tick() {
        Iterator<Map.Entry<Vec3, Float>> iterator = this.spreadDistances.entrySet().iterator();
        if (!this.isPlaying) {
            //更新已有声源的传播距离
            while (iterator.hasNext()) {
                Map.Entry<Vec3, Float> entry = iterator.next();
                Vec3 position = entry.getKey();
                float distance = entry.getValue();
                if (distance > this.ranges.get(position)) {
                    //移除已达到传播范围上限的声源
                    iterator.remove();
                }
                float speed = getSoundSpeed(
                        position.scale(0.5).add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().scale(0.5)),
                        Minecraft.getInstance().level);
                this.spreadDistances.put(position, distance + speed * 0.05f);
            }
        } else {
            if (iterator.hasNext()) {
                Vec3 position = iterator.next().getKey();
                float pitch = this.pitches.getOrDefault(position, 1.0f);
                float volume = this.volumes.getOrDefault(position, 1.0f);
                this.x = position.x;
                this.y = position.y;
                this.z = position.z;
                this.pitch = pitch;
                this.volume = volume;
                iterator.remove();
            }
        }
        if (ISoundSpreader != null) {
            //记录最新的声源位置速度
            var position = ISoundSpreader.getPosition(getSoundEvent());
            this.spreadDistances.put(position, 0F);
            this.speeds.put(position, ISoundSpreader.getSpeed(getSoundEvent()));
            this.pitches.put(position, ISoundSpreader.getPitch(getSoundEvent()));
            this.volumes.put(position, ISoundSpreader.getVolume(getSoundEvent()));
        }
    }

    @Nullable
    public SoundBuffer getSoundBuffer() {
        SoundData soundData = SoundModule.getSound(getSoundEvent().getLocation());
        if (soundData == null) {
            return SpreadingSoundHelper.INSTANCE.getSoundBuffer(getSoundEvent().getLocation());
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
        if (event.getSound() instanceof SpreadingSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
            }
        }
    }

    public static float getSoundSpeed(Vec3 position, ClientLevel level) {
        //TODO:高度挂钩，维度设定挂钩
        return 100f;
    }

    public Vec3 getSpeed(Vec3 position) {
        return this.speeds.get(position);
    }

    public float getRange(Vec3 position) {
        return this.ranges.get(position);
    }

    public float getPitch(Vec3 position) {
        return this.pitches.get(position);
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    public float getVolume(Vec3 position) {
        return this.volumes.get(position);
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public SoundEvent getSoundEvent() {
        return this.soundEvent;
    }
}
