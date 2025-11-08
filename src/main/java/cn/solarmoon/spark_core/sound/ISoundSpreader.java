package cn.solarmoon.spark_core.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

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

    /**
     * 获取声源的实时位置
     *
     * <p>此方法在每个游戏tick都会被调用，用于更新声音波面的发射源位置。
     * 返回的位置将作为声音传播的起点，声音会从此位置以音速向外传播。</p>
     *
     * <p>实现注意事项：</p>
     * <ul>
     *   <li>应返回当前帧声源在世界中的精确位置</li>
     *   <li>位置变化应平滑，避免剧烈跳跃</li>
     *   <li>对于移动声源，建议返回质心或主要发声部位的位置</li>
     * </ul>
     *
     * @param uuid 声源的UUID
     * @param event 当前播放的声音事件
     * @return 声源在当前游戏刻的三维世界坐标，单位：方块
     */
    @NotNull
    Vec3 getPosition(UUID uuid, SoundEvent event);

    /**
     * 获取声源的实时运动速度
     *
     * <p>此速度用于计算多普勒效应，即声音频率因声源与收听者相对运动而发生的变化。
     * 速度向量的大小和方向会影响收听者听到的音高。</p>
     *
     * <p>物理原理：</p>
     * <ul>
     *   <li>声源朝向收听者运动时，音调变高（频率增加）</li>
     *   <li>声源远离收听者运动时，音调变低（频率降低）</li>
     *   <li>速度向量应反映声源在当前游戏刻的瞬时速度</li>
     * </ul>
     *
     * @param uuid 声源的UUID
     * @param event 当前播放的声音事件
     * @return 声源的运动速度向量，单位：方块/秒（注意：不是每tick）
     */
    @NotNull
    Vec3 getSpeed(UUID uuid, SoundEvent event);

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
     * @param uuid 声源的UUID
     * @param event 当前播放的声音事件
     * @return 音高乘数，有效范围通常为 [0.5, 2.0]
     */
    float getPitch(UUID uuid, SoundEvent event);

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
     * @param uuid 声源的UUID
     * @param event 当前播放的声音事件
     * @return 声源原始音量，范围 [0.0, 1.0]
     */
    float getVolume(UUID uuid, SoundEvent event);

}