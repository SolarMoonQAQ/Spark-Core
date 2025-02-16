package cn.solarmoon.spark_core.molang.core.context;

import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.Random;

public interface IContext<TEntity> {
    TEntity entity();

    Minecraft mc();

    Level level();

    Random random();

    <TChild> IContext<TChild> createChild(TChild child);

    ITempVariableStorage tempStorage();

    IScopedVariableStorage scopedStorage();

    IForeignVariableStorage foreignStorage();

    float getPartialTick();

}
