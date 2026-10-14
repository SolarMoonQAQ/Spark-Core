package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import org.jetbrains.annotations.Nullable;

public interface LevelPatch extends PhysicsHost, TaskSubmitOffice {

    /**
     * <p>获取此 Level 的 PhysicsLevel，未就绪时返回 null（不抛异常）。</p>
     * <p>Get the PhysicsLevel of this Level, or null when it is not yet initialized.</p>
     *
     * <p>与 {@link PhysicsHost#getPhysicsLevel()} 的区别仅在于未就绪时的行为：
     * 后者抛 IllegalStateException，本方法返回 null。多数既有调用点「未就绪即异常」是正确语义，
     * 本入口只服务客户端渲染 / tick 一类需要守卫的调用方。</p>
     */
    @Nullable
    PhysicsLevel getPhysicsLevelOrNull();
}
