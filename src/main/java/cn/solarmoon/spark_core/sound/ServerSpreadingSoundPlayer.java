package cn.solarmoon.spark_core.sound;

import cn.solarmoon.spark_core.sound.payload.SpreadingSoundPayload;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class ServerSpreadingSoundPlayer implements ISpreadingSoundPlayer {
    /**
     * 通过网络包播放声音，不应被手动调用
     */
    public void playSpreadingSoundFromPacket(Level level, UUID uuid, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut) {
        if (level instanceof ServerLevel) {
            throw new UnsupportedOperationException("should not be called on server-side level");
        }
        else throw new IllegalStateException("method was called on a client-side level");
    }

    /**
     * 在定点播放可传播的声音
     * @return UUID 用于标识该声音的唯一ID
     */
    public UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float pitch, float volume, int fadeIn, int fadeOut) {
        if (level instanceof ServerLevel serverLevel) {
            UUID uuid = UUID.randomUUID();
            PacketDistributor.sendToPlayersNear(
                    serverLevel,
                    null,
                    position.x, position.y, position.z,
                    3 * soundEvent.getRange(1.0f),
                    new SpreadingSoundPayload(uuid, soundEvent, soundType, position, speed, pitch, volume, fadeIn, fadeOut)
            );
            return uuid;
        }
        else throw new IllegalStateException("method was called on a client-side level");
    }

    /**
     * 创建并绑定可传播声音至音源，并播放
     * @return UUID 用于标识该声音的唯一ID
     */
    public UUID playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISoundSpreader ISoundSpreader, int fadeIn, int fadeOut) {
        if (level instanceof ServerLevel serverLevel) {
            //TODO: 怎么找到客户端对应的ISpreadingSoundSource？
            throw new UnsupportedOperationException("method was called on a server-side level, but client-side ISoundSpreader is not supported yet");
        } else throw new IllegalStateException("method was called on a client-side level");
    }

    @Override
    public UUID transitionSound(Level level, UUID oldSoundSource, SoundEvent newSoundEvent, SoundSource soundType, ISoundSpreader soundSpreader, int fadeIn, int fadeOut) {
        throw new UnsupportedOperationException("server-side transitionSound is not supported yet");
    }

    @Override
    public SoundBuffer getSoundBuffer(ResourceLocation location) {
        return null;
    }
}
