package cn.solarmoon.spark_core.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface ISpreadingSoundPlayer {
    void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume);

    void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource);
}
