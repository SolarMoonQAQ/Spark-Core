package cn.solarmoon.spark_core.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

public interface ISoundSpreader {
    Vec3 getPosition(SoundEvent event);

    Vec3 getSpeed(SoundEvent event);

    float getRange(SoundEvent event);

    float getPitch(SoundEvent event);

    float getVolume(SoundEvent event);
}
