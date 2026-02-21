package cn.solarmoon.spark_core.mixin.terrain;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Unique
    private static PhysicsGhostObject spark_core$activator = null; // mixin类加载时物理库尚未加载，此时无法实例化对象，故再需要时再实例化

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onBlockStateChange(BlockPos pos, BlockState blockState, BlockState newState, CallbackInfo ci) {
        if (blockState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && newState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return; // 更新前后均为完整方块，无需更新区块方体形状
        PhysicsLevel level = SparkLevel.getPhysicsLevel(((ServerLevel) (Object) this));
        PhysicsChunkManager terrainManager = level.getTerrainManager();
        if (terrainManager != null) {
            terrainManager.onBlockUpdated(Set.of(pos));
        }
        // 地形更新时激活附近刚体，避免悬浮
        if (spark_core$activator == null) // 首次激活时实例化激活检测器
            spark_core$activator = new PhysicsGhostObject(new SphereCollisionShape(1f));
        level.submitDeduplicatedTask("terrain_update_activate_" + pos.toString(), PPhase.ALL, () -> {
            var bodies = spark_core$activator.getOverlappingObjects();
            for (PhysicsCollisionObject pco : bodies) {
                if (pco instanceof PhysicsRigidBody body && body.isDynamic() && !body.isActive())
                    body.activate();
            }
            return null;
        });
    }
}
