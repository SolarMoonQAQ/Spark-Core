package cn.solarmoon.spark_core.particle.common.data.component;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;

/**
 * 发射器形状组件的抽象适配器基类。<br>
 * 所有形状组件继承此类，共用 offset 和 direction 字段。
 */
public abstract class EmitterShapeAdapter implements IEmitterComponentDefinition {

    protected final String[] offset;
    protected final String[] direction;

    public EmitterShapeAdapter(String[] offset, String[] direction) {
        this.offset = offset;
        this.direction = direction;
    }

    @Override
    public int order() {
        return 0;
    }

    public String[] getOffset() {
        return offset;
    }

    public String[] getDirection() {
        return direction;
    }
}
