package cn.solarmoon.spark_core.particle.common;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.api.ParticleEffects;
import com.jme3.math.Transform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 粒子定位器锚点接口 —— 为持续粒子效果提供 locator 的实时世界空间位姿。
 *
 * <p>与 {@code ISoundSpreader} 设计理念一致：实现者作为"位姿提供者"，
 * 粒子系统每 tick 轮询此接口更新发射器位置与朝向。
 * 便利方法直接定义在接口上，无需经过 api 包。
 *
 * <p>典型场景：炮口抽烟、引擎尾焰、命中火花等绑定到 locator 的持续/跟随粒子效果。
 *
 * @see cn.solarmoon.spark_core.sound.ISoundSpreader
 */
public interface IParticleAnchor {

    /**
     * 获取指定 locator 在世界空间中的实时位姿（JME 坐标系）。
     * 粒子系统每 tick 调用此方法更新绑定发射器的位置与朝向。
     *
     * @param instanceId  发射器实例 UUID（同一锚点可能同时有多个特效）
     * @param locatorName 定位器名称（如 "muzzle"、"smoke_port"）
     * @return 世界空间变换；null 表示定位器不存在（发射器保持上次位姿）
     */
    @Nullable
    Transform getLocatorTransform(@NotNull UUID instanceId, @NotNull String locatorName);

    /**
     * 获取锚点运动速度（方块/秒），用于粒子继承发射平台速度。
     * 默认返回零向量。
     */
    @Nullable
    default Vec3 getAnchorVelocity(@NotNull UUID instanceId, @NotNull String locatorName) {
        return Vec3.ZERO;
    }

    // ——— 便利方法（仿 ISoundSpreader 模式）———

    /**
     * 播放绑定到定位器的持久粒子效果。
     * 发射器每 tick 轮询此锚点的 locator 位姿以跟随移动。
     * 仅在客户端调用有效。
     *
     * @param level       维度
     * @param effectId    粒子效果标识符
     * @param locatorName 定位器名称
     * @return 效果实例 UUID（可用于 {@link #stopEffect}），服务端返回 null
     */
    @Nullable
    default UUID playEffect(@NotNull Level level, @NotNull ResourceLocation effectId,
                             @NotNull String locatorName) {
        if (!level.isClientSide()) {
            SparkCore.LOGGER.warn("IParticleAnchor.playEffect() 仅应在客户端调用！");
            return null;
        }
        return ParticleEffects.burstPersistent(level, effectId, this, locatorName);
    }

    /**
     * 停止由本锚点创建的指定粒子效果。
     */
    default void stopEffect(@NotNull Level level, @NotNull UUID instanceId) {
        ParticleEffects.stop(level, instanceId);
    }
}
