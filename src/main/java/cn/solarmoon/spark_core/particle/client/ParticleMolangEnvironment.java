package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.molang.runtime.value.MutableObjectBinding;
import cn.solarmoon.spark_core.molang.runtime.value.Value;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 粒子 Molang 求值环境。
 * 封装了 {@link ParticleMolangContext} 与 {@link MolangContextRegistry} 的交互。
 * 提供编译、绑定、求值一站式 API。
 * <p>
 * 同时持有当前 {@link Level} 引用，供需要碰撞检测等世界交互的运行时组件使用。
 */
public class ParticleMolangEnvironment {

    private final ParticleMolangContext context;
    private final MutableObjectBinding variableStorage;
    @Nullable
    private Level level;

    public ParticleMolangEnvironment() {
        this.context = new ParticleMolangContext();
        this.variableStorage = context.getVariableStorage();
    }

    /**
     * 编译 Molang 表达式（结果自动缓存）。
     *
     * @param expression MoLang 表达式字符串
     * @return 编译后的表达式，空或 null 输入返回零值表达式
     */
    public MolangExpression compile(String expression) {
        if (expression == null || expression.isEmpty()) return ctx -> 0;
        return MolangContextRegistry.compile(expression, context);
    }

    /**
     * 求值已编译的表达式。
     *
     * @param expr 已编译的 MoLang 表达式
     * @return 求值结果
     */
    public double evaluate(MolangExpression expr) {
        return expr.evaluate(context);
    }

    /**
     * 绑定发射器级变量，供下一轮 Molang 求值使用。
     *
     * @param age      发射器当前存活时间（秒）
     * @param lifetime 发射器最大生命周期（秒）
     * @param randoms  4 个发射器随机数（范围为 [0, 1)）
     */
    public void bindEmitter(double age, double lifetime, double[] randoms) {
        context.bindEmitter(age, lifetime, randoms);
    }

    /**
     * 绑定粒子级变量，供下一轮 Molang 求值使用。
     *
     * @param age      粒子当前存活时间（秒）
     * @param lifetime 粒子最大生命周期（秒）
     * @param randoms  4 个粒子随机数（范围为 [0, 1)）
     */
    public void bindParticle(double age, double lifetime, double[] randoms) {
        context.bindParticle(age, lifetime, randoms);
    }

    /**
     * 设置自定义变量（如曲线求值结果）。
     * 变量通过 {@code variable.xxx} 或 {@code v.xxx} 在 Molang 表达式中访问。
     */
    public void setVariable(String name, double value) {
        variableStorage.set(name, Value.of(value));
    }

    /**
     * 获取底层 Molang 上下文（供高级操作，如直接操作 variable storage）。
     */
    public ParticleMolangContext getContext() {
        return context;
    }

    /**
     * 设置当前 Level 引用。由发射器在 tick 时设置，供需要世界交互的组件使用。
     */
    public void setLevel(@Nullable Level level) {
        this.level = level;
    }

    /**
     * 获取当前 Level 引用。可能为 null（反序列化阶段或服务端 stub）。
     */
    @Nullable
    public Level getLevel() {
        return level;
    }
}
