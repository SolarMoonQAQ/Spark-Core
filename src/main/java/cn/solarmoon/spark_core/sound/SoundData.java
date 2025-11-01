package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.pack.modules.SoundModule;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

public record SoundData(ByteBuffer byteBuffer, AudioFormat audioFormat)  {
    public void register(ResourceLocation resourceLocation){
        SoundModule.registerGeneratedSound(resourceLocation, this);
    }
}
