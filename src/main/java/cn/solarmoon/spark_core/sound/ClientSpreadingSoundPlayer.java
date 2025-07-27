package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundManagerMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientSpreadingSoundPlayer implements ISpreadingSoundPlayer {
    public void playSpreadingSound(SpreadingSoundInstance soundInstance) {
        ISoundManagerMixin soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        soundManager.machine_Max$playSpreading(soundInstance);
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        playSpreadingSound(new SpreadingSoundInstance(soundEvent, soundType, position, speed, range, pitch, volume));
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource) {
        playSpreadingSound(new SpreadingSoundInstance(soundEvent, soundType, ISpreadingSoundSource));
    }
}
