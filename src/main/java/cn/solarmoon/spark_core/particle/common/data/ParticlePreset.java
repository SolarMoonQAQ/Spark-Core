package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.particle.common.data.component.lifetime.ParticleLifetimeEvents;

import java.util.List;

/**
 * 粒子预设：包含粒子级的所有运行时组件和原始组件定义。
 * 构建时已按 order() 排序，并分离出 requireUpdate 子集。
 */
public class ParticlePreset {
    private final List<IParticleComponent> updateComponents;
    private final IParticleComponent[] allComponents;
    private final List<IComponentDefinition> definitions; // 原始定义列表，用于事件查找
    private final BillboardMode faceCameraMode;
    private final boolean hasLighting;

    public ParticlePreset(List<IParticleComponent> allComponents,
                          List<IComponentDefinition> definitions,
                          BillboardMode faceCameraMode, boolean hasLighting) {
        this.allComponents = allComponents.toArray(new IParticleComponent[0]);
        this.updateComponents = allComponents.stream().filter(c -> true).toList();
        this.definitions = definitions;
        this.faceCameraMode = faceCameraMode;
        this.hasLighting = hasLighting;
    }

    public IParticleComponent[] getAllComponents() { return allComponents; }
    public List<IParticleComponent> getUpdateComponents() { return updateComponents; }
    public List<IComponentDefinition> getDefinitions() { return definitions; }
    public BillboardMode getFaceCameraMode() { return faceCameraMode; }
    public boolean hasLighting() { return hasLighting; }

    /**
     * 从原始定义中查找粒子生命周期事件组件。
     */
    public ParticleLifetimeEvents findLifetimeEvents() {
        for (IComponentDefinition def : definitions) {
            if (def instanceof ParticleLifetimeEvents evts) return evts;
        }
        return null;
    }

    /** 11 种 Billboard 朝向模式 */
    public enum BillboardMode {
        ROTATE_XYZ,
        ROTATE_Y,
        LOOKAT_XYZ,
        LOOKAT_Y,
        LOOKAT_DIRECTION,
        DIRECTION_X,
        DIRECTION_Y,
        DIRECTION_Z,
        EMITTER_TRANSFORM_XY,
        EMITTER_TRANSFORM_XZ,
        EMITTER_TRANSFORM_YZ
    }
}
