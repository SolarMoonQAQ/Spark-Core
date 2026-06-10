package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.particle.client.ParticleArray;
import cn.solarmoon.spark_core.particle.client.ParticleMolangEnvironment;

/**
 * 发射器运行时组件接口。
 * 每 tick 由发射器引擎调用。
 * <p>
 * 方法签名中的 molang 参数由调用方（发射器引擎）传入其自身的 Molang 环境，
 * 确保组件内部求值时使用的是已绑定了发射器/粒子变量的上下文。
 */
public interface IEmitterComponent {
    /** 每 tick 调用 */
    void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang);
    /** 新粒子生成时的回调 */
    default void onSpawn(ParticleArray buf, int particleIndex, ParticleMolangEnvironment molang) {}
    /** 发射器当前是否处于激活相位（默认 true，Looping 组件覆写以支持 active/sleep 循环） */
    default boolean isInActivePhase() { return true; }
}
