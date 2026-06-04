package cn.solarmoon.spark_core.util;

import cn.solarmoon.spark_core.physics.terrain.BlockPhysicsData;
import cn.solarmoon.spark_core.physics.terrain.BlockPhysicsDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.concurrent.ConcurrentHashMap;

public class BlockCollisionUtil {

    private static final BlockPhysicsData DEFAULT_DATA = new BlockPhysicsData(0.7f, 1f, 0.3f, 0f, 1f);

    // Block 级别的物理数据缓存：利用运行时不变性避免重复 DataMap 查询
    private static final ConcurrentHashMap<Block, BlockPhysicsData> PHYSICS_CACHE = new ConcurrentHashMap<>();

    /**
     * 清空物理数据缓存，强制下次查询重新走 DataMap。
     * 数据包热重载（/reload）后调用，确保新数据生效。
     */
    public static void invalidatePhysicsCache() {
        PHYSICS_CACHE.clear();
    }

    /**
     * 获取方块的物理数据（使用 Block 级别缓存，避免重复 DataMap 查找）
     * @param state 方块状态
     * @return 物理数据，默认值兜底
     */
    public static BlockPhysicsData getPhysicsData(BlockState state) {
        Block block = state.getBlock();
        // 用 ConcurrentHashMap.computeIfAbsent —— 线程安全，仅第一次走 DataMap
        return PHYSICS_CACHE.computeIfAbsent(block, b -> {
            BlockPhysicsData data = b.builtInRegistryHolder().getData(BlockPhysicsDataMaps.BLOCK_PHYSICS_DATA);
            return data != null ? data : DEFAULT_DATA;
        });
    }

    /**
     * 获取方块的摩擦系数
     * Get the friction coefficient of the block
     */
    public static float getBlockFriction(BlockState state) {
        return getPhysicsData(state).getFriction();
    }

    /**
     * 获取方块的滚动摩擦系数
     * Get the rolling friction coefficient of the block
     */
    public static float getBlockRollingFriction(BlockState state) {
        return getPhysicsData(state).getRollingFriction();
    }

    /**
     * 获取方块的弹性系数
     * Get the restitution coefficient of the block
     */
    public static float getRestitution(BlockState state) {
        return getPhysicsData(state).getRestitution();
    }

    /**
     * 基于已获取的 BlockPhysicsData 计算湿滑滑动系数（避免重复查找）
     * @param data 已获取的物理数据
     * @param chunk 区块
     * @param pos 方块的**世界坐标**（用于查询上方是否有流体/雨水）
     * @return 最终滑动系数 [0, 1]
     */
    public static float calcSlip(BlockPhysicsData data, ChunkAccess chunk, BlockPos pos) {
        float humidity = 0.0f;
        // 湿滑处理
        if (!chunk.getFluidState(pos.above()).isEmpty()) {
            humidity = 1.0f;
        } else if (chunk.getLevel() != null && chunk.getLevel().isRainingAt(pos.above())) {
            humidity = chunk.getLevel().getRainLevel(1.0f);
        }
        return Math.min(1.0f, data.getBaseSlip() + humidity * data.getSlipFactor());
    }

    /**
     * 获取方块的滑动系数，考虑湿度影响
     * Get the sliding coefficient of the block, considering humidity effects
     */
    public static float getSlip(ChunkAccess chunk, BlockState state, BlockPos pos) {
        return calcSlip(getPhysicsData(state), chunk, pos);
    }

}