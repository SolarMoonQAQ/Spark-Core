// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.runtime.MolangContext;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.value.ObjectProperty;
import cn.solarmoon.spark_core.molang.runtime.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Spark-Core 的 MoLang 求值上下文基类，对标 {@link IAnimatable} 体系。
 * <p>
 * 泛型 T 对应具体的 {@link IAnimatable} 子类型，使得子类可通过
 * {@code getEntity().getAnimatable()} 直接获取目标对象而无需强转：
 * <pre>
 * // Spark-Core: 一般 IAnimatable
 * SparkMolangContext&lt;IAnimatable&lt;?&gt;&gt; ctx = new SparkMolangContext&lt;&gt;(animatable);
 *
 * // Machine-Max: SubPart
 * class SubPartMolangContext extends SparkMolangContext&lt;IAnimatable&lt;SubPart&gt;&gt;
 * SubPart sp = getEntity().getAnimatable(); // 直接是 SubPart，无需 instanceof
 * </pre>
 *
 * @param <T> 继承 {@link IAnimatable} 的具体类型
 */
public class SparkMolangContext<T extends IAnimatable<?>> extends MolangContext<T> {

    private final AnimatableVariableBinding variableBridge;

    public SparkMolangContext() {
        this(null);
    }

    public SparkMolangContext(@Nullable T animatable) {
        super(animatable);
        this.variableBridge = new AnimatableVariableBinding(animatable);
        // 将 AnimatableVariableBinding 的初始内容复制到 MolangContext 的 variable 存储中
        for (Map.Entry<String, ObjectProperty> entry : variableBridge.entries().entrySet()) {
            ObjectProperty prop = entry.getValue();
            if (prop != null) {
                Value val = prop.value();
                if (val != null) {
                    getVariableStorage().set(entry.getKey(), val);
                }
            }
        }
    }

    /**
     * 重置此上下文以用于一次新的求值。
     *
     * @param animatable 关联的动画体
     * @param animTime   当前动画时间（秒）
     */
    public void reset(T animatable, double animTime) {
        setEntity(animatable);
        prepareEvaluation((float) animTime);
        this.variableBridge.setAnimatable(animatable);
    }

    public AnimatableVariableBinding getVariableBridge() {
        return variableBridge;
    }

    // === 动画控制器相关查询 ===
    // 注意：框架内置 q → query 全局简写映射，因此这些方法同时支持 query.xxx 和 q.xxx

    @QueryBinding("all_animations_finished")
    public double queryAllAnimationsFinished() {
        T entity = getEntity();
        return (entity != null && entity.getControllerAllAnimationsFinished()) ? 1.0 : 0.0;
    }

    @QueryBinding("any_animation_finished")
    public double queryAnyAnimationFinished() {
        T entity = getEntity();
        return (entity != null && entity.getControllerAnyAnimationFinished()) ? 1.0 : 0.0;
    }
}
