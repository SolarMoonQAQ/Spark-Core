package cn.solarmoon.spark_core.mixin.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundEngineMixin;
import cn.solarmoon.spark_core.mixin_interface.ISoundManagerMixin;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SoundManager.class)
public class SoundManagerMixin implements ISoundManagerMixin {
    @Final
    @Shadow
    private SoundEngine soundEngine;

    @Override
    public void machine_Max$playSpreading(SpreadingSoundInstance soundInstance) {
        ((ISoundEngineMixin) soundEngine).machine_Max$queueSpreadingSound(soundInstance);
    }
}
