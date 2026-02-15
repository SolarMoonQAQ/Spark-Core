package cn.solarmoon.spark_core.api;

import cn.solarmoon.spark_core.LevelPatch;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * <p>SparkCore 对 Level 扩展能力的统一安全访问入口。</p>
 * <p>Central safe access point for SparkCore extensions on Level.</p>
 *
 * <p>本类通过运行期 cast 访问由 Mixin 注入的接口，</p>
 * <p>This class accesses Mixin-injected interfaces via runtime casting,</p>
 *
 * <p>避免使用 interface injection，确保在 Sodium / Dev 环境下稳定。</p>
 * <p>avoiding interface injection to ensure stability with Sodium and dev runtime.</p>
 */
public final class SparkLevel {

    private SparkLevel() {
    }

    /* ------------------------------------------------------------ */
    /* PhysicsHost                                                   */
    /* ------------------------------------------------------------ */

    /**
     * <p>获取 Level 对应的 PhysicsLevel。</p>
     * <p>Get the PhysicsLevel associated with this Level.</p>
     *
     * @throws ClassCastException <p>若 Level 未被 SparkCore 正确 Mixin。</p>
     *                            <p>Thrown if Level is not patched by SparkCore.</p>
     */
    public static @NotNull PhysicsLevel getPhysicsLevel(@NotNull Level level) {
        return ((PhysicsHost) level).getPhysicsLevel();
    }

    /**
     * <p>获取所有物理实体映射。</p>
     * <p>Get all physics collision bodies mapped to this Level.</p>
     */
    public static @NotNull Map<String, ?> getAllPhysicsBodies(@NotNull Level level) {
        return ((PhysicsHost) level).getAllPhysicsBodies();
    }

    /**
     * <p>根据名称获取物理实体。</p>
     * <p>Get a physics body by its name.</p>
     */
    public static @Nullable Object getPhysicsBody(
            @NotNull Level level,
            @NotNull String name
    ) {
        return ((PhysicsHost) level).getPhysicsBody(name);
    }

    /* ------------------------------------------------------------ */
    /* TaskSubmitOffice                                              */
    /* ------------------------------------------------------------ */

    /**
     * <p>提交一个带去重 key 的任务。</p>
     * <p>Submit a deduplicated task with the given key.</p>
     *
     * <p>若同一 phase + key 已存在任务，将被覆盖。</p>
     * <p>If a task with the same phase + key exists, it will be replaced.</p>
     */
    public static void submitDeduplicatedTask(
            @NotNull Level level,
            @NotNull String key,
            @NotNull PPhase phase,
            @NotNull Runnable task
    ) {
        ((TaskSubmitOffice) level).submitDeduplicatedTask(
                key, phase, () -> {
                    task.run();
                    return null;
                }
        );
    }

    /**
     * <p>提交一个即时任务。</p>
     * <p>Submit an immediate task.</p>
     */
    public static void submitImmediateTask(
            @NotNull Level level,
            @NotNull PPhase phase,
            @NotNull Runnable task
    ) {
        ((TaskSubmitOffice) level).submitImmediateTask(
                phase, () -> {
                    task.run();
                    return null;
                }
        );
    }

    /**
     * <p>处理指定阶段的所有任务。</p>
     * <p>Process all tasks registered for the given phase.</p>
     */
    public static void processTasks(
            @NotNull Level level,
            @NotNull PPhase phase
    ) {
        ((TaskSubmitOffice) level).processTasks(phase);
    }

    /* ------------------------------------------------------------ */
    /* Optional safety                                               */
    /* ------------------------------------------------------------ */

    /**
     * <p>检测 Level 是否已被 SparkCore Mixin。</p>
     * <p>Check whether the Level is patched by SparkCore.</p>
     */
    public static boolean isSparkLevel(@NotNull Level level) {
        return level instanceof LevelPatch;
    }
}
