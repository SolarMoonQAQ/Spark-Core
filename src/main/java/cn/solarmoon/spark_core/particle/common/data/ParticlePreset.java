package cn.solarmoon.spark_core.particle.common.data;

import java.util.List;

/**
 * 粒子预设：包含粒子级的所有运行时组件。
 * 构建时已按 order() 排序，并分离出 requireUpdate 子集。
 */
public class ParticlePreset {
    private final List<IParticleComponent> updateComponents;
    private final IParticleComponent[] allComponents;
    private final BillboardMode faceCameraMode;
    private final boolean hasLighting;

    public ParticlePreset(List<IParticleComponent> allComponents,
                          BillboardMode faceCameraMode, boolean hasLighting) {
        this.allComponents = allComponents.toArray(new IParticleComponent[0]);
        this.updateComponents = allComponents.stream().filter(c -> true).toList();
        this.faceCameraMode = faceCameraMode;
        this.hasLighting = hasLighting;
    }

    public IParticleComponent[] getAllComponents() { return allComponents; }
    public List<IParticleComponent> getUpdateComponents() { return updateComponents; }
    public BillboardMode getFaceCameraMode() { return faceCameraMode; }
    public boolean hasLighting() { return hasLighting; }

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
