package cn.solarmoon.spark_core.mixin_interface;


import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;

public interface ISoundEngineMixin {
    void spark_core$queueSpreadingSound(SpreadingSoundInstance sound);

    SoundBuffer spark_core$getSoundBuffer(ResourceLocation location);
}
