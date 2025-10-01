package cn.solarmoon.spark_core.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Objects;

public class BlockCollisionUtil {
    /**
     * 获取方块的摩擦系数，可在此通过mixin额外扩展逻辑<p>
     * Get the friction coefficient of the block, which can be extended by additional logic with mixin
     *
     * @return 方块的摩擦系数，与接触刚体的摩擦系数的乘积将作为实际相对摩擦系数 <p> The friction coefficient of the block, whose product of the friction coefficient of the contacting body is the actual relative friction coefficient
     */
    public static float getBlockFriction(Level level, BlockState state, BlockPos pos) {
        if (state.isStickyBlock()) return 10.0f; //粘性块拥有极大摩擦系数
        return 2 * (1 - state.getFriction(level, pos, null));
    }

    public static float getBlockRollingFriction(Level level, BlockState state, BlockPos pos) {
        if (state.isStickyBlock()) return 9.0f; //粘性块拥有极大摩擦系数
        else if (state.is(BlockTags.SNOW)) return 7.0f;
        else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return 5.0f;
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return 0.5f;
        else if (state.is(BlockTags.MINEABLE_WITH_AXE)) return 0.9f;
        else if (state.is(BlockTags.MINEABLE_WITH_HOE)) return 3.0f;
        else return 2f / state.getBlock().getSpeedFactor();
    }

    /**
     * 获取方块的滑动系数，影响不同速度下摩擦系数的变化规律。可在此通过mixin额外扩展逻辑<p>
     * Get the sliding coefficient of the block, which affects the friction coefficient change under different speeds. It can be extended by additional logic with mixin
     *
     * @return 方块的滑动系数，0-1之间，0表示完全不影响摩擦系数，1表示摩擦系数将在打滑时归零 <p> The sliding coefficient of the block, ranging from 0 to 1, 0 indicating that the friction coefficient will not be affected at all, and 1 indicating that the friction coefficient will be zeroed out when sliding
     */
    public static float getSlip(ChunkAccess chunk, BlockState state, BlockPos pos) {
        //TODO:细化逻辑
        if (state.isStickyBlock()) return 0;//粘性块不受滑动影响
        float result = 0.0f;
        float humidity = 0.0f;
        //湿滑处理
        if (!chunk.getFluidState(pos.above()).isEmpty()) humidity = 1.0f;
        else if (Objects.requireNonNull(chunk.getLevel()).isRainingAt(pos.above())) {
            humidity = Objects.requireNonNull(chunk.getLevel()).getRainLevel(1.0f);
        }
        if (state.is(BlockTags.SNOW)) {//雪块视作湿滑，疏松多孔，更易打滑
            result += 0.35f - (0.2f * humidity);
        } else if (state.getBlock() instanceof ColoredFallingBlock) {//可掉落方块视作颗粒状，疏松多孔，更易打滑
            result += (0.35f + 0.3f * humidity);
        } else if (state.getBlock() instanceof ConcretePowderBlock) {
            result += 0.35f - (0.5f * humidity);
        } else {//常规方块仅根据湿度调整摩擦系数
            result += (int) (0.3 * humidity);
        }
        return Math.min(1, result);
    }

    /**
     * <p>获取方块的弹性系数，影响刚体与方块碰撞时的弹性表现，0为完全非弹性碰撞，1为完全弹性碰撞。可在此通过mixin额外扩展逻辑。</p>
     * Get the elasticity coefficient of the block, which affects the elasticity of the body and the block when colliding, 0 indicating a completely inelastic collision, and 1 indicating a completely elastic collision. It can be extended by additional logic with mixin.
     *
     * @return 方块的弹性系数，0-1之间，0表示完全非弹性碰撞，1表示完全弹性碰撞 <p> The elasticity coefficient of the block, ranging from 0 to 1, 0 indicating a completely inelastic collision, and 1 indicating a completely elastic collision.
     */
    public static float getRestitution(ChunkAccess chunk, BlockState state, BlockPos pos) {
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return 0.3f;
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return 0.9f;
        else if (state.is(BlockTags.MINEABLE_WITH_AXE)) return 0.8f;
        else if (state.is(BlockTags.MINEABLE_WITH_HOE)) return 0.0f;
        else if (state.is(BlockTags.WOOL)) return 0.1f;//吸能方块
        else if (state.isSlimeBlock()) return 5f;
        else if (state.isStickyBlock()) return 0.0f;
        else return 0.5f;
    }
}
