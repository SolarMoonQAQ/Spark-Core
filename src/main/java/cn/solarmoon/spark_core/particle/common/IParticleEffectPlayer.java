package cn.solarmoon.spark_core.particle.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 粒子效果播放器接口（双端共享）。
 * 参考 ISpreadingSoundPlayer 的工厂模式。
 * 方法在 common 包中双端可用，当前仅客户端实际实现。
 */
public interface IParticleEffectPlayer {

    /**
     * 在指定位置触发粒子效果（定点播放，双端可用）。
     *
     * @param level    维度
     * @param effectId 粒子效果标识符
     * @param position 位置
     * @param rotation 旋转
     */
    void playEffect(Level level, ResourceLocation effectId,
                    Vec3 position, Vec3 rotation);

    /**
     * 触发粒子效果并绑定到 locator（双端可用）。
     *
     * @param level    维度
     * @param effectId 粒子效果标识符
     * @param locator  定位器名称
     * @param entityId 实体 UUID
     */
    void playEffect(Level level, ResourceLocation effectId,
                    String locator, UUID entityId);

    /**
     * 停止指定粒子的播放（双端可用）。
     *
     * @param level            维度
     * @param effectInstanceId 效果实例 UUID
     */
    void stopEffect(Level level, UUID effectInstanceId);
}
