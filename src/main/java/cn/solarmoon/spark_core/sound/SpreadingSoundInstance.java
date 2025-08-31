package cn.solarmoon.spark_core.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 声音传播范围的实现参考自https://github.com/MCModderAnchor/TACZ
 */
@OnlyIn(Dist.CLIENT)
public class SpreadingSoundInstance extends AbstractTickableSoundInstance {
    @Nullable
    public final ISpreadingSoundSource ISpreadingSoundSource;
    public final SoundEvent soundEvent;
    public final ResourceLocation name;
    public final LinkedHashMap<Vec3, Float> spreadDistances = new LinkedHashMap<>(20);
    public final LinkedHashMap<Vec3, Vec3> speeds = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> ranges = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> pitches = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> volumes = new LinkedHashMap<>(20);
    public boolean isPlaying = false;

    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例</p>
     *
     * @param soundEvent
     * @param soundType
     * @param position
     * @param speed
     * @param pitch
     * @param volume
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        this(soundEvent, soundType, soundEvent.getLocation(), position, speed, range, pitch, volume);
    }

    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例，可特别指定声源名称供子类使用</p>
     *
     * @param soundEvent
     * @param soundType
     * @param name
     * @param position
     * @param speed
     * @param range
     * @param pitch
     * @param volume
     */
    protected SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, ResourceLocation name, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        super(soundEvent, soundType, SoundInstance.createUnseededRandom());
        this.ISpreadingSoundSource = null;
        this.attenuation = Attenuation.NONE;
        this.soundEvent = soundEvent;
        this.name = name;
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
     * @param soundEvent
     * @param soundType
     * @param ISpreadingSoundSource
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource) {
        this(soundEvent, soundType, soundEvent.getLocation(), ISpreadingSoundSource);
    }

    /**
     * <p>针对持续发出声音的声源的构造函数，用于创建位置速度音高等时刻改变声源的音效实例，可特别指定声源名称供子类使用</p>
     *
     * @param soundEvent
     * @param soundType
     * @param name
     * @param ISpreadingSoundSource
     */
    protected SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, ResourceLocation name, ISpreadingSoundSource ISpreadingSoundSource) {
        super(soundEvent, soundType, SoundInstance.createUnseededRandom());
        this.ISpreadingSoundSource = ISpreadingSoundSource;
        this.attenuation = Attenuation.NONE;
        this.soundEvent = soundEvent;
        this.name = name;
        this.x = ISpreadingSoundSource.getPosition(name).x;
        this.y = ISpreadingSoundSource.getPosition(name).y;
        this.z = ISpreadingSoundSource.getPosition(name).z;
        this.pitch = ISpreadingSoundSource.getPitch(name);
        this.volume = ISpreadingSoundSource.getVolume(name);
        this.spreadDistances.put(ISpreadingSoundSource.getPosition(name), 0F);
        this.speeds.put(ISpreadingSoundSource.getPosition(name), ISpreadingSoundSource.getSpeed(name));
        this.ranges.put(ISpreadingSoundSource.getPosition(name), ISpreadingSoundSource.getRange(name));
        this.pitches.put(ISpreadingSoundSource.getPosition(name), this.pitch);
        this.volumes.put(ISpreadingSoundSource.getPosition(name), this.volume);
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
        if (ISpreadingSoundSource != null) {
            //记录最新的声源位置速度
            this.spreadDistances.put(ISpreadingSoundSource.getPosition(name), 0F);
            this.speeds.put(ISpreadingSoundSource.getPosition(name), ISpreadingSoundSource.getSpeed(name));
            this.pitches.put(ISpreadingSoundSource.getPosition(name), ISpreadingSoundSource.getPitch(name));
            this.volumes.put(ISpreadingSoundSource.getPosition(name), ISpreadingSoundSource.getVolume(name));
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
}
