package cn.solarmoon.spark_core.mixin.terrain;

import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onBlockStateChange(BlockPos pos, BlockState blockState, BlockState newState, CallbackInfo ci) {
        PhysicsChunkManager terrainManager = ((ServerLevel) (Object) this).getPhysicsLevel().getTerrainManager();
        if (terrainManager != null) {
            terrainManager.onBlockUpdated(Set.of(pos));
        }
    }
}
