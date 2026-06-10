package cn.solarmoon.spark_core.particle.common.data;

import java.util.List;

/**
 * 发射器预设：包含发射器级的所有运行时组件和原始定义组件。
 * 构建时已按 order() 排序。
 */
public class EmitterPreset {
    private final List<IEmitterComponent> updateComponents;
    private final IEmitterComponent[] allComponents;
    private final List<IComponentDefinition> definitions; // 原始定义组件（用于查询速率/形状/生命周期参数）
    private final boolean hasLocalPosition;
    private final boolean hasLocalRotation;
    private final boolean hasLocalVelocity;

    public EmitterPreset(List<IEmitterComponent> allComponents,
                         List<IComponentDefinition> definitions,
                         boolean hasLocalPosition, boolean hasLocalRotation, boolean hasLocalVelocity) {
        this.allComponents = allComponents.toArray(new IEmitterComponent[0]);
        this.updateComponents = allComponents.stream()
                .filter(c -> true) // 所有组件默认需要 update
                .toList();
        this.definitions = definitions;
        this.hasLocalPosition = hasLocalPosition;
        this.hasLocalRotation = hasLocalRotation;
        this.hasLocalVelocity = hasLocalVelocity;
    }

    public IEmitterComponent[] getAllComponents() { return allComponents; }
    public List<IEmitterComponent> getUpdateComponents() { return updateComponents; }
    public List<IComponentDefinition> getDefinitions() { return definitions; }
    public boolean hasLocalPosition() { return hasLocalPosition; }
    public boolean hasLocalRotation() { return hasLocalRotation; }
    public boolean hasLocalVelocity() { return hasLocalVelocity; }
}
