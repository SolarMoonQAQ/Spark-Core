package cn.solarmoon.spark_core.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public interface ISpreadingSoundSource {
    Vec3 getPosition(ResourceLocation name);

    Vec3 getSpeed(ResourceLocation name);

    float getRange(ResourceLocation name);

    float getPitch(ResourceLocation name);

    float getVolume(ResourceLocation name);
}
