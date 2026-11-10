package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 动态声源接口
 *
 * <p>实现此接口的类可以作为一个持续发声的声源，用于创建具有动态位置、速度、
 * 音量等属性的传播音效。音效系统会通过此接口的方法实时获取声源状态。</p>
 *
 * <p>适用场景：</p>
 * <ul>
 *   <li>移动的实体音效（车辆、生物等）</li>
 *   <li>位置变化的机械音效</li>
 *   <li>需要实时更新属性的环境音效</li>
 * </ul>
 *
 * @see SpreadingSoundInstance
 */
public interface ISoundSpreader {

    /** 返回声源所属世界，用于选择播放端和限制实例的生命周期。 */
    @NotNull
    Level getLevel();

    /**
     * 获取声源的实时位置
     *
     * <p>传播音效每个游戏 tick 用它生成波面，FMOD 3D 事件每个客户端渲染帧用它更新实例位置。
     * 实现应只读，可在同一游戏 tick 内多次取样。</p>
     *
     * <p>实现注意事项：</p>
     * <ul>
     *   <li>应返回当前帧声源在世界中的精确位置</li>
     *   <li>位置变化应平滑，避免剧烈跳跃</li>
     *   <li>对于移动声源，建议返回质心或主要发声部位的位置</li>
     * </ul>
     *
     * @param uuid 播放实例的 UUID
     * @return 声源的三维世界坐标，单位为方块
     */
    @NotNull
    Vec3 getPosition(UUID uuid);

    /**
     * 获取声源的实时运动速度
     *
     * <p>传播音效和 FMOD 3D 事件使用此世界速度计算多普勒效应。
     * 实现应只读，可在同一游戏 tick 内多次取样。</p>
     *
     * <p>物理原理：</p>
     * <ul>
     *   <li>声源朝向收听者运动时，音调变高（频率增加）</li>
     *   <li>声源远离收听者运动时，音调变低（频率降低）</li>
     *   <li>速度向量应反映声源在当前游戏刻的瞬时速度</li>
     * </ul>
     *
     * @param uuid 播放实例的 UUID
     * @return 声源的运动速度向量，单位：方块/秒（注意：不是每tick）
     */
    @NotNull
    default Vec3 getSpeed(UUID uuid) {
        return Vec3.ZERO;
    }

    /**
     * 获取声源的实时音高（频率倍数）
     *
     * <p>音高值是一个乘数因子，用于调整声音的播放频率：</p>
     * <ul>
     *   <li>1.0 = 原声音高</li>
     *   <li>2.0 = 提高一个八度（频率翻倍）</li>
     *   <li>0.5 = 降低一个八度（频率减半）</li>
     * </ul>
     *
     * <p>此音高值会在多普勒效应计算的基础上进一步叠加，允许实现基于
     * 声源状态的音高变化（如引擎转速变化）。</p>
     *
     * @param uuid 播放实例的 UUID
     * @return 音高乘数，有效范围通常为 [0.5, 2.0]
     */
    default float getPitch(UUID uuid) {
        return 1.0f;
    }

    /**
     * 获取声源的实时音量
     *
     * <p>音量值表示声源在发声位置的原始音量强度，不考虑距离衰减。
     * 实际到达收听者的音量会根据距离平方反比定律进行衰减。</p>
     *
     * <p>音量范围：</p>
     * <ul>
     *   <li>0.0 = 完全静音</li>
     *   <li>1.0 = 最大音量（音频文件的原始音量）</li>
     *   <li>大于1.0的值可能造成削波失真，不推荐使用</li>
     * </ul>
     *
     * @param uuid 播放实例的 UUID
     * @return 声源原始音量，范围 [0.0, 1.0]
     */
    default float getVolume(UUID uuid) {
        return 1.0f;
    }

    /**
     * 判断本地听者是否应听车内版本。客户端主线程在播放前及 FMOD 播放期间调用。
     * 返回 true 时，传播音效优先选择 {@code _interior} 缓冲，FMOD 将可写的 {@code interior} 设为 1。
     * 实现只读取听者和声源状态。
     */
    default boolean shouldApplyInteriorEffect(@Nullable Entity cameraEntity, boolean firstPerson) {
        return false;
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于持续播放且移动的声音，仅在客户端调用时有效
     *
     * @param soundEvent 声音事件，随用随建，用于指定声音传播范围和音效路径
     * @param soundType  声音类型，方块，环境等
     * @param fadeIn     声音的淡入时间 (tick)
     * @param fadeOut    声音的淡出时间 (tick)
     * @return UUID 用于标识该声音的唯一ID
     */
    @Nullable
    default UUID playSpreadingSound(SoundEvent soundEvent, SoundSource soundType, int fadeIn, int fadeOut, boolean loop) {
        return SpreadingSoundHelper.playSpreadingSound(soundEvent, soundType, this, fadeIn, fadeOut, loop);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效，适用于持续播放且移动的声音，仅在客户端调用时有效
     *
     * @param soundEvent 声音事件，随用随建，用于指定声音传播范围和音效路径
     * @param soundType  声音类型，方块，环境等
     * @return UUID 用于标识该声音的唯一ID
     */
    @Nullable
    default UUID playSpreadingSound(SoundEvent soundEvent, SoundSource soundType) {
        return playSpreadingSound(soundEvent, soundType, 0, 0, false);
    }

    /**
     * 播放带音速延迟和多普勒效果的扩散音效并将其与现有的音效交叉淡入淡出(CrossFadeIn/Out)，适用引擎等音效会发生变化的音源，仅在客户端调用时有效
     *
     * @param oldSoundSource 旧声源标识（UUID）
     * @param newSoundEvent  新音效事件
     * @param soundType      声音类型
     * @param fadeIn         淡入时长
     * @param fadeOut        淡出时长
     * @return 新实例的UUID
     */
    @Nullable
    default UUID transitionSound(UUID oldSoundSource, SoundEvent newSoundEvent, SoundSource soundType, int fadeIn, int fadeOut, boolean loop) {
        return SpreadingSoundHelper.transitionSound(oldSoundSource, newSoundEvent, soundType, this, fadeIn, fadeOut, loop);
    }

    /** 开始淡出此声源的传播音效实例。 */
    default void fadeSound(UUID uuid) {
        Level level = getLevel();
        if (level.isClientSide()) SpreadingSoundHelper.fadeSound(level, uuid);
    }

    /** 立即停止此声源的传播音效实例。 */
    default void stopSound(UUID uuid) {
        Level level = getLevel();
        if (level.isClientSide()) SpreadingSoundHelper.stopSound(level, uuid);
    }

    /** 查询指定 FMOD bank 是否已在当前客户端加载。服务端返回 false。 */
    default boolean isFmodBankLoaded(ResourceLocation bankId) {
        return SpreadingSoundHelper.isFmodBankLoaded(getLevel(), bankId);
    }

    /**
     * 播放指定 FMOD 事件。3D 事件跟随此声源，2D 事件在听者原位播放。
     * 客户端在启动前应用初始参数和听者判定，成功时返回实例 UUID。
     * 绑定声源后，局部参数 {@code interior} 由播放器维护。
     * 服务端或事件无法启动时返回 null，客户端调用方可按需回退到传播音效。
     */
    @Nullable
    default UUID tryPlayFmodSound(ResourceLocation bankId, String eventPath,
                                 SoundSource category, Map<String, Float> initialParams) {
        return SpreadingSoundHelper.tryPlayFmodSound(this, bankId, eventPath, category, initialParams);
    }

    /** 为指定 FMOD 实例更新一个局部参数，服务端调用返回 false。 */
    default boolean setFmodParameter(@Nullable UUID uuid, String name, float value) {
        return SpreadingSoundHelper.setFmodParameter(getLevel(), uuid, name, value);
    }

    /** 按 Studio 事件的停止过渡淡出，服务端调用返回 false。 */
    default boolean fadeFmodSound(@Nullable UUID uuid) {
        return SpreadingSoundHelper.fadeFmodSound(getLevel(), uuid);
    }

    /** 立即停止并释放指定 FMOD 实例，服务端调用返回 false。 */
    default boolean stopFmodSound(@Nullable UUID uuid) {
        return SpreadingSoundHelper.stopFmodSound(getLevel(), uuid);
    }

}
