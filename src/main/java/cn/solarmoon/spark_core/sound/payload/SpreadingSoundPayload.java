package cn.solarmoon.spark_core.sound.payload;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SpreadingSoundPayload(
        UUID uuid,
        SoundEvent soundEvent,
        SoundSource soundSource,
        Vec3 position,
        Vec3 velocity,
        float pitch,
        float volume,
        int fadeIn,
        int fadeOut
) implements CustomPacketPayload {
    public static final Type<SpreadingSoundPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "play_spreading_sound_payload"));
    public static final StreamCodec<FriendlyByteBuf, SpreadingSoundPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SpreadingSoundPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID uuid = buffer.readUUID();
            SoundEvent soundEvent = SoundEvent.DIRECT_STREAM_CODEC.decode(buffer);
            SoundSource soundSource = buffer.readEnum(SoundSource.class);
            Vec3 position = buffer.readVec3();
            Vec3 velocity = buffer.readVec3();
            float pitch = buffer.readFloat();
            float volume = buffer.readFloat();
            int fadeIn = buffer.readInt();
            int fadeOut = buffer.readInt();
            return new SpreadingSoundPayload(uuid, soundEvent, soundSource, position, velocity, pitch, volume, fadeIn, fadeOut);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, SpreadingSoundPayload value) {
            buffer.writeUUID(value.uuid());
            SoundEvent.DIRECT_STREAM_CODEC.encode(buffer, value.soundEvent());
            buffer.writeEnum(value.soundSource());
            buffer.writeVec3(value.position());
            buffer.writeVec3(value.velocity());
            buffer.writeFloat(value.pitch());
            buffer.writeFloat(value.volume());
            buffer.writeInt(value.fadeIn());
            buffer.writeInt(value.fadeOut());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final SpreadingSoundPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> SpreadingSoundHelper.playSpreadingSoundFromPacket(
                context.player().level(),
                payload.uuid(),
                payload.soundEvent(),
                payload.soundSource(),
                payload.position(),
                payload.velocity(),
                payload.pitch(),
                payload.volume(),
                payload.fadeIn(),
                payload.fadeOut()
        ));
    }
}
