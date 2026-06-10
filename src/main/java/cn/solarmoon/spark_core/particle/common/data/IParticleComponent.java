package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.particle.client.ParticleArray;
import cn.solarmoon.spark_core.particle.client.ParticleMolangEnvironment;

/**
 * 粒子运行时组件接口。
 * 每 tick 由粒子引擎对每个存活粒子调用。
 * <p>
 * 方法签名中的 molang 参数由调用方（发射器引擎）传入其自身的 Molang 环境，
 * 确保组件内部求值时使用的是当前粒子的已绑定上下文（particle_age 等变量正确）。
 */
public interface IParticleComponent {
    /** 粒子每 tick 调用 (主线程, 含运动积分) */
    default void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {}
    /** 粒子生成时一次调用 */
    default void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {}
}
