package cn.solarmoon.spark_core.particle.common.data;

/**
 * 粒子组件定义接口（定义层）。
 * 每个粒子组件定义可通过 createRuntime() 创建对应的运行时组件。
 */
public interface IParticleComponentDefinition extends IComponentDefinition {
    /** 创建运行时组件实例。默认返回无操作实现。 */
    default IParticleComponent createRuntime() {
        return new IParticleComponent() {};
    }
    /** 此组件是否需要每 tick 更新 */
    default boolean requireUpdate() { return true; }
}
