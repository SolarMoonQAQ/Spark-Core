package cn.solarmoon.spark_core.mixin_interface;


import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;

public interface ISoundManagerMixin {
    void spark_core$playSpreading(SpreadingSoundInstance soundInstance);


    void spark_core$playSpreadingImmediately(SpreadingSoundInstance soundInstance);

    SoundBuffer spark_core$getSoundBuffer(ResourceLocation location);
}
