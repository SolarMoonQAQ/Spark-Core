package cn.solarmoon.spark_core.particle.common.data;

/**
 * 组件定义基接口（不可变，JSON 反序列化产物）。
 * 所有粒子/发射器组件定义均实现此接口。
 */
public interface IComponentDefinition {
    /** 执行顺序，值越小越先执行 */
    int order();
}
