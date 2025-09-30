package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import org.jetbrains.annotations.NotNull;

public interface BlockEntityPatch extends Syncer {

    @Override
    default SyncerType getSyncerType() {
        return null;
    }

    @Override
    default SyncData getSyncData() {
        return null;
    }

}
