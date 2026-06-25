package cn.solarmoon.spark_core.api;

import cn.solarmoon.spark_core.LevelPatch;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.ChunkHeightIndex;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
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
     * <p>提交一个去重延迟任务，将在 processTasks(phase) 被调用 delayTicks 次后执行。</p>
     * <p>Submit a deduplicated delayed task that executes after N processTasks(phase) calls.</p>
     *
     * <p>同一 phase + key 会覆盖旧的延迟任务（去重）。</p>
     * <p>Same phase + key will overwrite the previous delayed task (dedup).</p>
     *
     * @param key        任务唯一标识 / task unique key for dedup
     * @param phase      任务执行阶段 / task execution phase
     * @param delayTicks 延迟 tick 数 / delay in ticks (processTasks calls)
     * @param task       要执行的任务 / task to run
     */
    public static void submitDelayedTask(
            @NotNull Level level,
            @NotNull String key,
            @NotNull PPhase phase,
            int delayTicks,
            @NotNull Runnable task
    ) {
        ((TaskSubmitOffice) level).submitDelayedTask(
                key, phase, delayTicks, () -> {
                    task.run();
                    return null;
                }
        );
    }

    /**
     * <p>提交一个非去重延迟任务，每次调用均新增，互不覆盖。</p>
     * <p>Submit a non-deduplicated delayed task; each call adds a new independent task.</p>
     *
     * @param level      目标 Level / target level
     * @param phase      任务执行阶段 / task execution phase
     * @param delayTicks 延迟 tick 数 / delay in ticks (processTasks calls)
     * @param task       要执行的任务 / task to run
     */
    public static void submitDelayedTask(
            @NotNull Level level,
            @NotNull PPhase phase,
            int delayTicks,
            @NotNull Runnable task
    ) {
        ((TaskSubmitOffice) level).submitDelayedTask(
                phase, delayTicks, () -> {
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

    /* ------------------------------------------------------------ */
    /* ChunkHeightIndex — 区块实体方块高程索引                        */
    /* ------------------------------------------------------------ */

    /**
     * <p>获取用于管理此 Level 地形刚体的 PhysicsChunkManager。</p>
     * <p>Get the PhysicsChunkManager that manages terrain rigid bodies for this Level.</p>
     *
     * @param level 目标 Level / target level
     * @return PhysicsChunkManager 实例，不会为 null（服务端 Level 已初始化物理世界）
     */
    private static @NotNull PhysicsChunkManager terrainManager(@NotNull Level level) {
        return getPhysicsLevel(level).getTerrainManager();
    }

    /**
     * <p>获取此 Level 的区块固体高度索引。</p>
     * <p>Get the chunk height index for this Level.</p>
     *
     * @param level 目标 Level / target level
     * @return 索引对象，客户端始终返回 null（索引仅服务端维护）
     */
    public static @Nullable ChunkHeightIndex getChunkHeightIndex(@NotNull Level level) {
        if (level.isClientSide) return null;
        return terrainManager(level).getChunkHeightIndex();
    }

    /**
     * <p>查询指定区块位置在指定 Y 区间内是否存在实体方块。</p>
     * <p>Query whether there are solid blocks in the given Y range at the given chunk position.</p>
     *
     * @param level 目标 Level / target level
     * @param pos   任意方块坐标（自动转换为 ChunkPos） / any block position, auto-converted to ChunkPos
     * @param yMin  查询 Y 下界 / lower bound Y (MC coordinates)
     * @param yMax  查询 Y 上界 / upper bound Y (MC coordinates)
     * @return true = 存在实体方块；false = 无数据或不存在
     */
    public static boolean hasSolidInRange(
            @NotNull Level level,
            @NotNull BlockPos pos,
            double yMin,
            double yMax
    ) {
        ChunkHeightIndex index = getChunkHeightIndex(level);
        if (index == null) return false;
        return index.hasSolidInRange(new ChunkPos(pos), (short) yMin, (short) yMax);
    }

    /**
     * <p>查询指定区块的完整固体区间列表。</p>
     * <p>Get all solid intervals for the given chunk.</p>
     *
     * @param level    目标 Level / target level
     * @param chunkPos 区块坐标 / chunk position
     * @return 区间数组 [min1,max1, min2,max2, ...]，无数据返回 null；调用方不得修改返回的数组
     */
    public static short @Nullable [] getSolidIntervals(
            @NotNull Level level,
            @NotNull ChunkPos chunkPos
    ) {
        ChunkHeightIndex index = getChunkHeightIndex(level);
        return index != null ? index.getIntervals(chunkPos) : null;
    }

    /**
     * <p>检查指定区块是否已建立索引（即曾被加载过）。</p>
     * <p>Check whether the given chunk has been indexed (i.e. was loaded at least once).</p>
     */
    public static boolean hasChunkIndex(
            @NotNull Level level,
            @NotNull ChunkPos chunkPos
    ) {
        ChunkHeightIndex index = getChunkHeightIndex(level);
        return index != null && index.hasChunk(chunkPos);
    }

    /**
     * <p>预约在指定延迟后加载指定区块的指定 Y 范围地形，并保持一定 tick 后自动释放。</p>
     * <p>Schedule loading of terrain for the given chunk and Y range after a delay,
     * holding it for a given duration before auto-release.</p>
     *
     * <p>多次调度同一 chunk 取最晚过期（自动合并）。</p>
     * <p>Multiple schedules for the same chunk merge to the latest expiry.</p>
     *
     * <p>任意线程可调用，内部通过 submitImmediateTask 投递到主线程执行。</p>
     * <p>Thread-safe — defers to main thread via submitImmediateTask.</p>
     *
     * @param level      目标 Level / target level
     * @param chunkPos   目标区块 / target chunk
     * @param yMin       Y 下界 / lower Y bound
     * @param yMax       Y 上界 / upper Y bound
     * @param delayTicks 延迟 tick 数（0 = 立即）
     * @param holdTicks  加载就绪后保持 tick 数
     */
    public static void scheduleChunkLoad(
            @NotNull Level level,
            @NotNull ChunkPos chunkPos,
            double yMin,
            double yMax,
            int delayTicks,
            int holdTicks
    ) {
        PhysicsChunkManager mgr = terrainManager(level);
        int minSecY = SectionPos.blockToSectionCoord((int) yMin);
        int maxSecY = SectionPos.blockToSectionCoord((int) yMax);
        var yRange = new kotlin.ranges.IntRange(minSecY, maxSecY);
        // ★ 使用 PPhase.POST 而非 PPhase.ALL，确保 addRegionTicket 在 ChunkMap.tick() 之后执行
        // 避免在 ChunkMap.tick() 迭代 entityMap 时因内部数据结构被修改而导致 NPE
        if (delayTicks <= 0) {
            submitImmediateTask(level, PPhase.POST, () ->
                    mgr.scheduleTerrain(chunkPos, yRange, holdTicks)
            );
        } else {
            submitDelayedTask(level, "terrain_api_" + chunkPos, PPhase.POST, delayTicks, () ->
                    mgr.scheduleTerrain(chunkPos, yRange, holdTicks)
            );
        }
    }

    /**
     * <p>检查指定区块的指定 Y 范围地形是否已就绪。</p>
     * <p>Check whether terrain for the given chunk and Y range is ready.</p>
     *
     * @return true = 地形刚体已在物理世界中可用（已加载 + 已构建 + 已激活）
     */
    public static boolean isChunkTerrainReady(
            @NotNull Level level,
            @NotNull ChunkPos chunkPos,
            double yMin,
            double yMax
    ) {
        PhysicsChunkManager mgr = terrainManager(level);
        int minSecY = SectionPos.blockToSectionCoord((int) yMin);
        int maxSecY = SectionPos.blockToSectionCoord((int) yMax);
        return mgr.isTerrainReady(chunkPos, new kotlin.ranges.IntRange(minSecY, maxSecY));
    }

    /**
     * <p>取消对某区块的全部调度请求，立即释放 MC ticket 和物理区块。</p>
     * <p>Cancel all scheduled loads for a chunk and immediately release MC ticket and terrain.</p>
     *
     * <p>语义：投射物离开后立刻卸载。</p>
     * <p>Semantics: unload immediately when projectile leaves.</p>
     *
     * <p>任意线程可调用，内部通过 submitImmediateTask 投递到主线程执行。</p>
     * <p>Thread-safe — defers to main thread via submitImmediateTask.</p>
     */
    public static void cancelChunkLoad(
            @NotNull Level level,
            @NotNull ChunkPos chunkPos
    ) {
        submitImmediateTask(level, PPhase.ALL, () ->
            terrainManager(level).cancelAllSchedules(chunkPos)
        );
    }
}
