package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.particle.client.ParticleArray;
import cn.solarmoon.spark_core.particle.client.ParticleMolangEnvironment;
import cn.solarmoon.spark_core.particle.common.data.IEmitterComponent;

/**
 * 发射器组件定义接口（定义层）。
 * 每个发射器组件定义可通过 createRuntime() 创建对应的运行时组件。
 */
public interface IEmitterComponentDefinition extends IComponentDefinition {
    /** 创建运行时组件实例。默认返回无操作实现。 */
    default IEmitterComponent createRuntime() {
        return new IEmitterComponent() {
            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}
        };
    }
    /** 此组件是否需要每 tick 更新 */
    default boolean requireUpdate() { return true; }
}
