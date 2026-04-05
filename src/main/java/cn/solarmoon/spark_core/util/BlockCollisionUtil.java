package cn.solarmoon.spark_core.util;

import cn.solarmoon.spark_core.physics.terrain.BlockPhysicsData;
import cn.solarmoon.spark_core.physics.terrain.BlockPhysicsDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class BlockCollisionUtil {

    private static BlockPhysicsData getPhysicsData(BlockState state) {
        BlockPhysicsData data = state.getBlock().builtInRegistryHolder().getData(BlockPhysicsDataMaps.BLOCK_PHYSICS_DATA);
        return data != null ? data : getDefaultData();
    }

    private static BlockPhysicsData getDefaultData() {
        return new BlockPhysicsData(0.7f, 1f, 0.3f, 0f, 1f);
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
     * 获取方块的滑动系数，考虑湿度影响
     * Get the sliding coefficient of the block, considering humidity effects
     */
    public static float getSlip(ChunkAccess chunk, BlockState state, BlockPos pos) {
        BlockPhysicsData data = getPhysicsData(state);
        float baseSlip = data.getBaseSlip();
        float slipFactor = data.getSlipFactor();

        float humidity = 0.0f;
        // 湿滑处理
        if (!chunk.getFluidState(pos.above()).isEmpty()) {
            humidity = 1.0f;
        } else if (chunk.getLevel() != null && chunk.getLevel().isRainingAt(pos.above())) {
            humidity = chunk.getLevel().getRainLevel(1.0f);
        }

        return Math.min(1.0f, baseSlip + humidity * slipFactor);
    }

}