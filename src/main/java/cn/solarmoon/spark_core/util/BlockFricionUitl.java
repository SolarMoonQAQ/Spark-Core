package cn.solarmoon.spark_core.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Objects;

public class BlockFricionUitl {
    /**
     * 获取方块的摩擦系数，可在此额外扩展逻辑，例如特定方块有特殊的摩擦系数<p>
     * Get the friction coefficient of the block, which can be extended by additional logic, such as a special friction coefficient for specific blocks
     * @return 方块的摩擦系数，与接触刚体的摩擦系数的乘积将作为实际相对摩擦系数 <p> The friction coefficient of the block, whose product of the friction coefficient of the contacting body is the actual relative friction coefficient
     */
    public static float getBlockFriction(Level level, BlockState state, BlockPos pos) {
        return 2 * (1 - state.getFriction(level, pos, null));
    }

    /**
     * 获取方块的滑动系数，影响不同速度下摩擦系数的变化规律。可在此额外扩展逻辑，例如特定方块在特定条件下有特殊的滑动系数<p>
     * Get the sliding coefficient of the block, which affects the friction coefficient change under different speeds. It can be extended by additional logic, such as a special sliding coefficient for specific blocks under specific conditions
     * @return 方块的滑动系数，0-100之间，0表示完全不影响摩擦系数，100表示摩擦系数将在打滑时归零 <p> The sliding coefficient of the block, ranging from 0 to 100, 0 indicating that the friction coefficient will not be affected at all, and 100 indicating that the friction coefficient will be zeroed out when sliding
     */
    public static int getSlip(ChunkAccess chunk, BlockState state, BlockPos pos) {
        //TODO:细化逻辑
        if (Objects.requireNonNull(chunk.getLevel()).isRainingAt(pos) || !chunk.getFluidState(pos.above()).isEmpty()) return 99;
        else return 0;
    }
}
