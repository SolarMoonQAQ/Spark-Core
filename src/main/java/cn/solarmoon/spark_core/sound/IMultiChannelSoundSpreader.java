package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.SparkCore;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 多通道动态声源接口。
 *
 * <p>该接口用于描述“一个声源同时驱动多个声音通道”的场景（例如发动机/电机不同工况音轨），
 * 每个通道都维护自己的实例 UUID、权重和定期重播节奏。</p>
 */
public interface IMultiChannelSoundSpreader extends ISoundSpreader {

    /**
     * 声音通道数据。
     *
     * <p>每个通道对应一个 SoundEvent，并维护独立实例与重播计时。</p>
     */
    class SoundChannel {
        private final String channelKey;
        private final SoundEvent soundEvent;
        private UUID uuid;
        private float weight;
        private int retriggerIntervalTicks;
        private int lastRetriggerTick;

        public SoundChannel(String channelKey, SoundEvent soundEvent, int retriggerIntervalTicks) {
            this.channelKey = channelKey;
            this.soundEvent = soundEvent;
            this.retriggerIntervalTicks = retriggerIntervalTicks;
            this.lastRetriggerTick = Integer.MIN_VALUE;
            this.weight = 0.0f;
        }

        public String getChannelKey() {
            return channelKey;
        }

        public SoundEvent getSoundEvent() {
            return soundEvent;
        }

        public UUID getUuid() {
            return uuid;
        }

        public void setUuid(UUID uuid) {
            this.uuid = uuid;
        }

        public float getWeight() {
            return weight;
        }

        public void setWeight(float weight) {
            this.weight = Math.max(0.0f, weight);
        }

        public int getRetriggerIntervalTicks() {
            return retriggerIntervalTicks;
        }

        public void setRetriggerIntervalTicks(int retriggerIntervalTicks) {
            this.retriggerIntervalTicks = retriggerIntervalTicks;
        }

        public int getLastRetriggerTick() {
            return lastRetriggerTick;
        }

        public void setLastRetriggerTick(int lastRetriggerTick) {
            this.lastRetriggerTick = lastRetriggerTick;
        }
    }

    /**
     * 返回当前声源的全部通道。
     *
     * <p>key 必须稳定且唯一，用于权重更新和通道定位。</p>
     */
    @NotNull
    Map<String, SoundChannel> getSoundChannels();

    /**
     * 返回主音量（不含通道权重），默认 1。
     */
    default float getMasterVolume(UUID uuid, SoundEvent event) {
        return 1.0f;
    }

    /**
     * 根据 UUID 查找对应通道。
     */
    @Nullable
    default SoundChannel getChannel(UUID uuid) {
        for (SoundChannel channel : getSoundChannels().values()) {
            UUID channelUuid = channel.getUuid();
            if (channelUuid != null && channelUuid.equals(uuid)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * 音量 = 主音量 × 通道权重。
     */
    @Override
    default float getVolume(UUID uuid, SoundEvent event) {
        SoundChannel channel = getChannel(uuid);
        if (channel == null) return 0.0f;
        return Math.max(0.0f, getMasterVolume(uuid, event) * channel.getWeight());
    }

    /**
     * 每 tick 驱动多通道声音实例。
     *
     * <p>逻辑：
     * 1. 确保每个通道至少有一个活跃实例（首次播放）。
     * 2. 按各通道的重播间隔执行同事件过渡重播，避免长音轨断流。</p>
     */
    default void tickSoundChannels(Level level, SoundSource soundType, int currentTick, int fadeIn, int fadeOut) {
        if (!level.isClientSide()) {
            SparkCore.LOGGER.warn("IMultiChannelSoundSpreader.tickSoundChannels() should only be called on the client side!");
            return;
        }
        for (SoundChannel channel : getSoundChannels().values()) {
            SoundEvent event = channel.getSoundEvent();
            if (event == null) continue;

            // 首次进入时为通道创建实例，满足“多通道同时播放”的基础要求。
            if (channel.getUuid() == null) {
                UUID uuid = playSpreadingSound(level, event, soundType, fadeIn, fadeOut);
                channel.setUuid(uuid);
                channel.setLastRetriggerTick(currentTick);
                continue;
            }

            // 仅对有权重的通道进行周期重播，减少无效过渡。
            int interval = channel.getRetriggerIntervalTicks();
            if (interval > 0 && channel.getWeight() > 0.0f
                    && currentTick - channel.getLastRetriggerTick() >= interval) {
                UUID next = transitionSound(level, channel.getUuid(), event, soundType, fadeIn, fadeOut);
                channel.setUuid(next);
                channel.setLastRetriggerTick(currentTick);
            }
        }
    }
}
