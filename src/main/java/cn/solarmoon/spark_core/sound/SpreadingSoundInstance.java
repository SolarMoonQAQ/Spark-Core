package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.pack.modules.SoundModule;
import cn.solarmoon.spark_core.registry.common.SparkSounds;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * 带音速传播(非简单延迟，而是波面抵达收听者)和多普勒效应的音效实例
 * 声音传播范围的实现参考自https://github.com/MCModderAnchor/TACZ
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SpreadingSoundInstance extends AbstractTickableSoundInstance {
    private static final int MAX_SOUND_POINTS = 200; // 单实例最大声源点数量(10秒)

    public UUID uuid = UUID.randomUUID();
    @Nullable
    public final ISoundSpreader ISoundSpreader;
    private final SoundEvent soundEvent;
    public final float maxRange;

    // 声音传播点数据
    public final LinkedList<SoundSourcePoint> soundPoints = new LinkedList<>();
    // 声音传播范围的AABB边界框，用于快速剔除
    private AABB spreadAABB;

    public boolean isPlaying = false;
    public Vec3 speed = Vec3.ZERO;

    // 淡入淡出相关
    private final int fadeInTicks;
    private final int fadeOutTicks;
    private int fadeProgress; // 当前淡入淡出进度（正数表示淡入，负数表示淡出）
    private float fadeFactor; // 声音淡入淡出因子，用于计算声音的最终音量
    private boolean isFadingOut = false;
    private boolean shouldGenerateNewPoints = true;

    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例</p>
     *
     * @param soundEvent 声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType  声音类型，方块，环境等
     * @param position   声音位置
     * @param speed      声音发出时的速度
     * @param maxRange   声音的最大距离
     * @param pitch      声音的音高
     * @param volume     声音的音量
     * @param fadeInTicks   声音淡入的时长，单位：tick
     * @param fadeOutTicks  声音淡出的时长，单位：tick
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float maxRange, float pitch, float volume, int fadeInTicks, int fadeOutTicks) {
        super(SparkSounds.getCUSTOM_SOUND().get(), soundType, SoundInstance.createUnseededRandom());
        this.ISoundSpreader = null;
        this.attenuation = Attenuation.NONE;//衰减根据与听者的距离自动调整
        this.soundEvent = soundEvent;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.pitch = pitch;
        this.volume = volume;
        this.maxRange = maxRange;
        this.fadeInTicks = fadeInTicks;
        this.fadeOutTicks = fadeOutTicks;
        this.fadeProgress = (fadeInTicks > 0) ? 0 : 1; // 如果没有淡入，立即完成
        this.fadeFactor = fadeProgress;

        // 创建初始声音点
        SoundSourcePoint soundPoint = new SoundSourcePoint(position, speed, pitch, volume);
        this.soundPoints.add(soundPoint);
        // 初始化AABB
        updateAABB();
    }

    /**
     * <p>针对持续发出声音的声源的构造函数，用于创建位置速度音高等时刻改变声源的音效实例</p>
     *
     * @param soundEvent    声音事件，随用随建时可用于分辨来自同一源的不同声音
     * @param soundType     声音类型，方块，环境等
     * @param soundSpreader 声源，音效会通过接口提供的方法更新其位置
     * @param maxRange      声音的最大距离
     * @param fadeInTicks   声音淡入的时长，单位：tick
     * @param fadeOutTicks  声音淡出的时长，单位：tick
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, ISoundSpreader soundSpreader, float maxRange, int fadeInTicks, int fadeOutTicks) {
        super(SparkSounds.getCUSTOM_SOUND().get(), soundType, SoundInstance.createUnseededRandom());
        this.ISoundSpreader = soundSpreader;
        this.attenuation = Attenuation.NONE;//衰减根据与听者的距离自动调整
        this.soundEvent = soundEvent;
        var position = soundSpreader.getPosition(this.uuid, soundEvent);
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.maxRange = maxRange;
        this.pitch = soundSpreader.getPitch(this.uuid, soundEvent);
        this.volume = soundSpreader.getVolume(this.uuid, soundEvent);
        this.speed = soundSpreader.getSpeed(this.uuid, soundEvent);
        this.fadeInTicks = fadeInTicks;
        this.fadeOutTicks = fadeOutTicks;
        this.fadeProgress = (fadeInTicks > 0) ? 0 : 1; // 如果没有淡入，立即完成
        this.fadeFactor = fadeProgress;

        // 创建初始声音点
        SoundSourcePoint soundPoint = new SoundSourcePoint(position, soundSpreader.getSpeed(this.uuid, soundEvent), this.pitch, this.volume);
        this.soundPoints.add(soundPoint);
        // 初始化AABB
        updateAABB();
    }

    @Override
    public void tick() {
        // 更新淡入淡出状态
        updateFadeState();

        Iterator<SoundSourcePoint> iterator = this.soundPoints.iterator();
        // 更新传播距离和生成新声源点
        while (iterator.hasNext()) {
            SoundSourcePoint soundPoint = iterator.next();

            if (soundPoint.getSpreadDistance() > this.maxRange) {
                // 移除已达到传播范围上限的声源
                iterator.remove();
                continue;
            }

            // 计算传播速度并更新距离
            float speed = getSoundSpeed(
                    soundPoint.getPosition().scale(0.5)
                            .add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().scale(0.5)),
                    Minecraft.getInstance().level);
            soundPoint.setSpreadDistance(soundPoint.getSpreadDistance() + speed * 0.05f);
        }

        if (ISoundSpreader != null && shouldGenerateNewPoints) {
            // 记录最新的声源位置速度等
            var position = ISoundSpreader.getPosition(this.uuid, getSoundEvent());
            SoundSourcePoint newSoundPoint = new SoundSourcePoint(
                    position,
                    ISoundSpreader.getSpeed(this.uuid, getSoundEvent()),
                    ISoundSpreader.getPitch(this.uuid, getSoundEvent()),
                    ISoundSpreader.getVolume(this.uuid, getSoundEvent())
            );
            this.soundPoints.add(newSoundPoint);
            if (this.soundPoints.size() > MAX_SOUND_POINTS) {
                this.soundPoints.removeFirst(); // 限制声源点数量
            }
        }

        // 更新传播范围的AABB
        updateAABB();
    }

    /**
     * 更新淡入淡出进度
     */
    private void updateFadeState() {
        if (fadeProgress < fadeInTicks && !isFadingOut) {
            // 淡入阶段
            fadeProgress++;
            fadeFactor = (float) fadeProgress / fadeInTicks;
        } else if (isFadingOut && fadeProgress > -fadeOutTicks) {
            // 淡出阶段
            fadeProgress--;
            fadeFactor = 1.0f - (float) Math.abs(fadeProgress) / fadeOutTicks;
            // 淡出完成且没有活跃波面时停止
            if (fadeProgress <= -fadeOutTicks && soundPoints.isEmpty()) {
                this.stop();
            }
        }
    }

    /**
     * 开始淡出
     */
    public void startFadeOut() {
        this.isFadingOut = true;
        this.shouldGenerateNewPoints = false;
        this.fadeProgress = 0; // 从当前进度开始淡出
    }

    /**
     * 从另一声音实例复制传播状态
     * @param other 另一声音实例
     */
    public void copySoundPointsFrom(SpreadingSoundInstance other) {
        this.soundPoints.clear();
        this.soundPoints.addAll(other.soundPoints);
        // 更新AABB
        this.updateAABB();
    }

    /**
     * 更新声音传播范围的AABB
     */
    public void updateAABB() {
        if (soundPoints.isEmpty()) {
            // 如果没有声源点，使用一个很小的AABB
            this.spreadAABB = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (SoundSourcePoint point : soundPoints) {
            Vec3 pos = point.getPosition();
            float spread = point.getSpreadDistance();

            // 计算该声源点当前传播范围的边界
            minX = Math.min(minX, pos.x - spread);
            minY = Math.min(minY, pos.y - spread);
            minZ = Math.min(minZ, pos.z - spread);
            maxX = Math.max(maxX, pos.x + spread);
            maxY = Math.max(maxY, pos.y + spread);
            maxZ = Math.max(maxZ, pos.z + spread);
        }

        // 添加一些容差，确保边界足够
        double tolerance = 1.0;
        this.spreadAABB = new AABB(
                minX - tolerance, minY - tolerance, minZ - tolerance,
                maxX + tolerance, maxY + tolerance, maxZ + tolerance
        );
    }

    /**
     * 获取当前声音传播范围的AABB
     */
    public AABB getSpreadAABB() {
        return spreadAABB;
    }

    /**
     * 检查收听者是否在声音传播范围内
     */
    public boolean isListenerInRange(Vec3 listenerPos) {
        return spreadAABB.contains(listenerPos);
    }

    /**
     * 应用指定的声源点属性到声音实例
     */
    public void applySoundPoint(SoundSourcePoint soundPoint) {
        this.x = soundPoint.getPosition().x;
        this.y = soundPoint.getPosition().y;
        this.z = soundPoint.getPosition().z;
        this.pitch = soundPoint.getPitch();
        this.volume = soundPoint.getVolume();
        this.speed = soundPoint.getSpeed();
        removeSoundPoint(soundPoint);
    }

    /**
     * 获取所有已到达收听者的声源点（按抵达时间排序）
     */
    public List<SoundSourcePoint> getReachedSoundPoints(Vec3 listenerPos) {
        List<SoundSourcePoint> reachedPoints = new ArrayList<>();
        for (SoundSourcePoint soundPoint : this.soundPoints) {
            if (soundPoint.hasReachedListener(listenerPos)) {
                reachedPoints.add(soundPoint);
            }
        }
        // 按传播距离排序（先到达的在前）
        reachedPoints.sort(Comparator.comparingDouble(SoundSourcePoint::getSpreadDistance));
        return reachedPoints;
    }

    /**
     * 移除指定的声源点
     */
    public void removeSoundPoint(SoundSourcePoint point) {
        this.soundPoints.remove(point);
    }

    @Nullable
    public SoundBuffer getSoundBuffer() {
        SoundData soundData = SoundModule.getSound(getSoundEvent().getLocation());
        if (soundData == null) {
            return SpreadingSoundHelper.INSTANCE.getSoundBuffer(getSoundEvent().getLocation());
        }

        AudioFormat rawFormat = soundData.audioFormat();

        // 格式验证
        if (!isValidAudioFormat(rawFormat)) {
            SparkCore.LOGGER.error("Invalid audio format: {}", rawFormat);
            return null;
        }

        // 创建原始缓冲区的副本，避免并发修改
        ByteBuffer buffer = soundData.byteBuffer().duplicate();

        if (buffer == null || buffer.capacity() == 0) {
            SparkCore.LOGGER.error("Invalid audio buffer");
            return null;
        }

        // 确保缓冲区位置为0
        buffer.rewind();

        try {
            if (rawFormat.getChannels() > 1) {
                // 转换为单声道
                AudioFormat monoFormat = new AudioFormat(
                        rawFormat.getEncoding(),
                        rawFormat.getSampleRate(),
                        rawFormat.getSampleSizeInBits(),
                        1, // 单声道
                        rawFormat.getSampleSizeInBits() / 8, // 单声道帧大小
                        rawFormat.getSampleRate(),
                        rawFormat.isBigEndian(),
                        rawFormat.properties()
                );

                ByteBuffer monoBuffer = convertToMono(buffer, rawFormat);
                if (monoBuffer == null) {
                    return null;
                }

                return new SoundBuffer(monoBuffer, monoFormat);
            }

            return new SoundBuffer(buffer, rawFormat);
        } catch (Exception e) {
            SparkCore.LOGGER.error("Failed to create SoundBuffer", e);
            return null;
        }
    }

    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (event.getSound() instanceof SpreadingSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();

            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);//将声音作为buffer附加到一个空的音频中，实现任意自定义音效的播放
                event.getChannel().play();
            }
        }
    }

    public static float getSoundSpeed(Vec3 position, ClientLevel level) {
        //TODO:与高度挂钩，与介质挂钩，与维度设定挂钩
        return 100f;
    }

    public Vec3 getSpeed() {
        return this.speed != null ? this.speed : Vec3.ZERO;
    }

    public float getMaxRange() {
        return this.maxRange;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    @Override
    public float getVolume() {
        return this.volume * this.fadeFactor;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public boolean isFadedOut() { return fadeProgress <= -fadeOutTicks; }
    public int getFadeProgress() { return fadeProgress; }
    public int getFadeInTicks() { return fadeInTicks; }
    public int getFadeOutTicks() { return fadeOutTicks; }
    public boolean shouldGenerateNewPoints() { return shouldGenerateNewPoints; }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public UUID getUUID() {
        return uuid;
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
    }

    public void setUUID(String uuid){
        this.uuid = UUID.fromString(uuid);
    }

    public SoundEvent getSoundEvent() {
        return this.soundEvent;
    }

    private boolean isValidAudioFormat(AudioFormat format) {
        return format.getSampleSizeInBits() == 16 &&
                format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED &&
                format.getSampleRate() >= 8000 && format.getSampleRate() <= 48000;
    }

    /**
     * 将多声道音频转换为单声道
     */
    private ByteBuffer convertToMono(ByteBuffer source, AudioFormat format) {
        try {
            int channels = format.getChannels();
            int sampleSize = format.getSampleSizeInBits() / 8; // 字节数
            int frameSize = format.getFrameSize();
            int totalFrames = source.capacity() / frameSize;

            // 单声道缓冲区大小
            int monoSize = totalFrames * sampleSize;
            ByteBuffer monoBuffer = ByteBuffer.allocateDirect(monoSize);

            source.rewind();

            for (int frame = 0; frame < totalFrames; frame++) {
                long sum = 0;

                // 对每个声道的样本求和
                for (int channel = 0; channel < channels; channel++) {
                    int samplePos = frame * frameSize + channel * sampleSize;
                    source.position(samplePos);

                    if (sampleSize == 2) {
                        sum += source.getShort();
                    } else {
                        // 处理其他位深度
                        SparkCore.LOGGER.warn("Unsupported sample size: {}", sampleSize);
                        return null;
                    }
                }

                // 计算平均值并写入单声道缓冲区
                short monoSample = (short) (sum / channels);
                monoBuffer.putShort(monoSample);
            }

            monoBuffer.rewind();
            source.rewind(); // 恢复原始缓冲区位置

            return monoBuffer;
        } catch (Exception e) {
            SparkCore.LOGGER.error("Failed to convert to mono", e);
            return null;
        }
    }
}