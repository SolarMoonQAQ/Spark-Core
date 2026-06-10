package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.SparkMolangContext;
import cn.solarmoon.spark_core.molang.runtime.value.Value;

/**
 * 粒子专用的 MoLang 求值上下文。
 * <p>
 * 所有 emitter_* / particle_* 内置变量通过 {@link #bindEmitter} /
 * {@link #bindParticle} 写入父类的 {@code variableStorage}（{@code MutableObjectBinding}），
 * 在 Molang 表达式中通过 {@code variable.xxx} / {@code v.xxx} 语法访问。
 * <p>
 * 重要约定: particle_age 始终映射到 tick 时累加的 age，
 * 而非插值时间。渲染线程不参与 Molang 求值。
 */
public class ParticleMolangContext extends SparkMolangContext<IAnimatable<?>> {

    public ParticleMolangContext() {
        super(null);
    }

    /**
     * 绑定发射器级变量。写入 variableStorage 使 Molang 的
     * {@code v.emitter_age}、{@code v.emitter_random_1} 等可正确求值。
     *
     * @param age      发射器当前存活时间（秒）
     * @param lifetime 发射器最大生命周期（秒）
     * @param randoms  4 个发射器随机数（范围为 [0, 1)）
     */
    public void bindEmitter(double age, double lifetime, double[] randoms) {
        getVariableStorage().set("emitter_age", Value.of(age));
        getVariableStorage().set("emitter_lifetime", Value.of(lifetime));
        getVariableStorage().set("emitter_random_1", Value.of(randoms[0]));
        getVariableStorage().set("emitter_random_2", Value.of(randoms[1]));
        getVariableStorage().set("emitter_random_3", Value.of(randoms[2]));
        getVariableStorage().set("emitter_random_4", Value.of(randoms[3]));
    }

    /**
     * 绑定粒子级变量。写入 variableStorage 使 Molang 的
     * {@code v.particle_age}、{@code v.particle_random_1} 等可正确求值。
     *
     * @param age      粒子当前存活时间（秒）
     * @param lifetime 粒子最大生命周期（秒）
     * @param randoms  4 个粒子随机数（范围为 [0, 1)）
     */
    public void bindParticle(double age, double lifetime, double[] randoms) {
        getVariableStorage().set("particle_age", Value.of(age));
        getVariableStorage().set("particle_lifetime", Value.of(lifetime));
        getVariableStorage().set("particle_random_1", Value.of(randoms[0]));
        getVariableStorage().set("particle_random_2", Value.of(randoms[1]));
        getVariableStorage().set("particle_random_3", Value.of(randoms[2]));
        getVariableStorage().set("particle_random_4", Value.of(randoms[3]));
    }
}
