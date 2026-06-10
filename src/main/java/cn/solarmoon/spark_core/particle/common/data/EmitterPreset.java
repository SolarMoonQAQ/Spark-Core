package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 发射器预设：包含发射器级的所有运行时组件、原始定义组件，以及
 * 发射器引擎直接使用的预编译 Molang 表达式。
 * <p>
 * 所有表达式在反序列化时预编译，跨发射器实例共享（同一粒子定义的所有实例
 * 共用同一份编译结果），避免每个 EmitterInstance 持有重复引用。
 */
public class EmitterPreset {
    private final List<IEmitterComponent> updateComponents;
    private final IEmitterComponent[] allComponents;
    private final List<IComponentDefinition> definitions;
    private final boolean hasLocalPosition;
    private final boolean hasLocalRotation;
    private final boolean hasLocalVelocity;

    // —— 预编译的 Molang 表达式（所有实例共享）——
    @Nullable private final MolangExpression spawnRateExpr;
    @Nullable private final MolangExpression maxParticlesExpr;
    @Nullable private final MolangExpression expirationExpr;
    @Nullable private final MolangExpression activeTimeExpr;
    @Nullable private final MolangExpression numParticlesExpr;

    public EmitterPreset(List<IEmitterComponent> allComponents,
                         List<IComponentDefinition> definitions,
                         boolean hasLocalPosition, boolean hasLocalRotation, boolean hasLocalVelocity,
                         @Nullable MolangExpression spawnRateExpr,
                         @Nullable MolangExpression maxParticlesExpr,
                         @Nullable MolangExpression expirationExpr,
                         @Nullable MolangExpression activeTimeExpr,
                         @Nullable MolangExpression numParticlesExpr) {
        this.allComponents = allComponents.toArray(new IEmitterComponent[0]);
        this.updateComponents = allComponents.stream()
                .filter(c -> true)
                .toList();
        this.definitions = definitions;
        this.hasLocalPosition = hasLocalPosition;
        this.hasLocalRotation = hasLocalRotation;
        this.hasLocalVelocity = hasLocalVelocity;
        this.spawnRateExpr = spawnRateExpr;
        this.maxParticlesExpr = maxParticlesExpr;
        this.expirationExpr = expirationExpr;
        this.activeTimeExpr = activeTimeExpr;
        this.numParticlesExpr = numParticlesExpr;
    }

    public IEmitterComponent[] getAllComponents() { return allComponents; }
    public List<IEmitterComponent> getUpdateComponents() { return updateComponents; }
    public List<IComponentDefinition> getDefinitions() { return definitions; }
    public boolean hasLocalPosition() { return hasLocalPosition; }
    public boolean hasLocalRotation() { return hasLocalRotation; }
    public boolean hasLocalVelocity() { return hasLocalVelocity; }

    /** 稳态发射速率表达式（EmitterRateSteady）。null 表示非稳态发射器 */
    @Nullable public MolangExpression getSpawnRateExpr() { return spawnRateExpr; }
    /** 最大粒子数表达式（EmitterRateSteady/Manual）。null 表示无限制 */
    @Nullable public MolangExpression getMaxParticlesExpr() { return maxParticlesExpr; }
    /** 发射器过期表达式（EmitterLifetimeExpression） */
    @Nullable public MolangExpression getExpirationExpr() { return expirationExpr; }
    /** 单次发射激活时长表达式（EmitterLifetimeOnce） */
    @Nullable public MolangExpression getActiveTimeExpr() { return activeTimeExpr; }
    /** 瞬时发射粒子数表达式（EmitterRateInstant） */
    @Nullable public MolangExpression getNumParticlesExpr() { return numParticlesExpr; }
}
