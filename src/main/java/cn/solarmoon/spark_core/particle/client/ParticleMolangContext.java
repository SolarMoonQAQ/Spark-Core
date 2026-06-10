package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.SparkMolangContext;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;

/**
 * 粒子专用的 MoLang 求值上下文。
 * 绑定 emitter_* 和 particle_* 系列变量。
 *
 * <p>重要约定: particle_age 始终映射到 tick 时累加的 age，
 * 而非插值时间。渲染线程不参与 Molang 求值。</p>
 */
public class ParticleMolangContext extends SparkMolangContext<IAnimatable<?>> {

    private double emitterAge;
    private double emitterLifetime;
    private final double[] emitterRandom = new double[4];

    private double particleAge;
    private double particleLifetime;
    private final double[] particleRandom = new double[4];

    public ParticleMolangContext() {
        super(null);
    }

    @QueryBinding("emitter_age")
    public double qEmitterAge() {
        return emitterAge;
    }

    @QueryBinding("emitter_lifetime")
    public double qEmitterLifetime() {
        return emitterLifetime;
    }

    @QueryBinding("emitter_random_1")
    public double qEmitterRandom1() {
        return emitterRandom[0];
    }

    @QueryBinding("emitter_random_2")
    public double qEmitterRandom2() {
        return emitterRandom[1];
    }

    @QueryBinding("emitter_random_3")
    public double qEmitterRandom3() {
        return emitterRandom[2];
    }

    @QueryBinding("emitter_random_4")
    public double qEmitterRandom4() {
        return emitterRandom[3];
    }

    @QueryBinding("particle_age")
    public double qParticleAge() {
        return particleAge;
    }

    @QueryBinding("particle_lifetime")
    public double qParticleLifetime() {
        return particleLifetime;
    }

    @QueryBinding("particle_random_1")
    public double qParticleRandom1() {
        return particleRandom[0];
    }

    @QueryBinding("particle_random_2")
    public double qParticleRandom2() {
        return particleRandom[1];
    }

    @QueryBinding("particle_random_3")
    public double qParticleRandom3() {
        return particleRandom[2];
    }

    @QueryBinding("particle_random_4")
    public double qParticleRandom4() {
        return particleRandom[3];
    }

    /**
     * 绑定发射器级变量，供下一轮 Molang 求值使用。
     *
     * @param age      发射器当前存活时间（秒）
     * @param lifetime 发射器最大生命周期（秒）
     * @param randoms  4 个发射器随机数（范围为 [0, 1)）
     */
    public void bindEmitter(double age, double lifetime, double[] randoms) {
        this.emitterAge = age;
        this.emitterLifetime = lifetime;
        System.arraycopy(randoms, 0, this.emitterRandom, 0, 4);
    }

    /**
     * 绑定粒子级变量，供下一轮 Molang 求值使用。
     *
     * @param age      粒子当前存活时间（秒）
     * @param lifetime 粒子最大生命周期（秒）
     * @param randoms  4 个粒子随机数（范围为 [0, 1)）
     */
    public void bindParticle(double age, double lifetime, double[] randoms) {
        this.particleAge = age;
        this.particleLifetime = lifetime;
        System.arraycopy(randoms, 0, this.particleRandom, 0, 4);
    }
}
