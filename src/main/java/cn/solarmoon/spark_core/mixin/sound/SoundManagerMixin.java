package cn.solarmoon.spark_core.mixin.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundEngineMixin;
import cn.solarmoon.spark_core.mixin_interface.ISoundManagerMixin;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SoundManager.class)
public class SoundManagerMixin implements ISoundManagerMixin {
    @Final
    @Shadow
    private SoundEngine soundEngine;

    @Override
    public void spark_core$playSpreading(SpreadingSoundInstance soundInstance) {
        ((ISoundEngineMixin) soundEngine).spark_core$queueSpreadingSound(soundInstance);
    }

    @Override
    public void spark_core$playSpreadingImmediately(SpreadingSoundInstance soundInstance) {
         ((ISoundEngineMixin) soundEngine).spark_core$ImmediatePlaySpreadingSound(soundInstance);
    }

    @Override
    public SoundBuffer spark_core$getSoundBuffer(ResourceLocation location) {
        return ((ISoundEngineMixin) soundEngine).spark_core$getSoundBuffer(location);
    }
}
