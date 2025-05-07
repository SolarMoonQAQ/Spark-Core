package cn.solarmoon.spark_core.util;

import cn.solarmoon.spark_core.SparkCore;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Objects;

public class BlockFricionUitl {
    /**
     * 获取方块的摩擦系数，可在此额外扩展逻辑，例如特定方块有特殊的摩擦系数<p>
     * Get the friction coefficient of the block, which can be extended by additional logic, such as a special friction coefficient for specific blocks
     *
     * @return 方块的摩擦系数，与接触刚体的摩擦系数的乘积将作为实际相对摩擦系数 <p> The friction coefficient of the block, whose product of the friction coefficient of the contacting body is the actual relative friction coefficient
     */
    public static float getBlockFriction(Level level, BlockState state, BlockPos pos) {
        if (state.isStickyBlock()) return 10.0f; //粘性块拥有极大摩擦系数
        return 2 * (1 - state.getFriction(level, pos, null));
    }

    /**
     * 获取方块的滑动系数，影响不同速度下摩擦系数的变化规律。可在此额外扩展逻辑，例如特定方块在特定条件下有特殊的滑动系数<p>
     * Get the sliding coefficient of the block, which affects the friction coefficient change under different speeds. It can be extended by additional logic, such as a special sliding coefficient for specific blocks under specific conditions
     *
     * @return 方块的滑动系数，0-1之间，0表示完全不影响摩擦系数，1表示摩擦系数将在打滑时归零 <p> The sliding coefficient of the block, ranging from 0 to 1, 0 indicating that the friction coefficient will not be affected at all, and 1 indicating that the friction coefficient will be zeroed out when sliding
     */
    public static int getSlip(ChunkAccess chunk, BlockState state, BlockPos pos) {
        //TODO:细化逻辑
        if (state.isStickyBlock()) return 0;//粘性块不受滑动影响
        int result = 0;
        float humidity = 0.0f;
        //湿滑处理
        if (!chunk.getFluidState(pos.above()).isEmpty()) humidity += 1.0f;
        else if (Objects.requireNonNull(chunk.getLevel()).isRainingAt(pos)) {//TODO:检查为什么canSeeSky恒返回false
            humidity += Objects.requireNonNull(chunk.getLevel()).getRainLevel(1.0f);
//            SparkCore.LOGGER.info("isRainingAt: {} humidity: {}", pos, humidity);
        }
        if (state.is(BlockTags.SNOW)) {//雪块视作湿滑，疏松多孔，更易打滑
            result += 35 - (int) (20 * humidity);
        } else if (state.getBlock() instanceof ColoredFallingBlock) {//可掉落方块视作颗粒状，疏松多孔，更易打滑
            result += (int) (35 + 40 * humidity);
        } else if (state.getBlock() instanceof ConcretePowderBlock) {
            result += 35 - (int) (50 * humidity);
        } else {//常规方块仅根据湿度调整摩擦系数
            result += (int) (50 * humidity);
        }
        return Math.min(100, result);
    }
}
