package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.registry.common.SparkSyncerTypes;
import cn.solarmoon.spark_core.sync.BlockPosSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements Syncer {

    @Shadow public abstract BlockPos getBlockPos();

    @Override
    public @NotNull SyncerType<?> getSyncerType() {
        return SparkSyncerTypes.INSTANCE.getBLOCK_ENTITY().get();
    }

    @Override
    public @NotNull SyncData getSyncData() {
        return new BlockPosSyncData(getBlockPos());
    }

}
