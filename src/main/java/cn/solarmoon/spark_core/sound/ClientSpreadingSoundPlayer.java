package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundManagerMixin;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ClientSpreadingSoundPlayer implements ISpreadingSoundPlayer {

    public static final RandomSource RANDOM = RandomSource.create();
    public static void playSpreadingSound(SpreadingSoundInstance soundInstance) {
        ISoundManagerMixin soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        soundManager.spark_core$playSpreading(soundInstance);
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        playSpreadingSound(new SpreadingSoundInstance(soundEvent, soundType, position, speed, range, pitch, volume));
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader ISoundSpreader) {
        playSpreadingSound(new SpreadingSoundInstance(soundEvent, soundType, ISoundSpreader));
    }

    @Override
    public @Nullable SoundBuffer getSoundBuffer(ResourceLocation location) {
        ISoundManagerMixin soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        WeighedSoundEvents weighedSoundEvents = ((SoundManager)soundManager).getSoundEvent(location);
        if (weighedSoundEvents == null) {
            return null;
        }else {
            Sound sound = weighedSoundEvents.getSound(RANDOM);
            return soundManager.spark_core$getSoundBuffer(sound.getPath());
        }
    }
}
