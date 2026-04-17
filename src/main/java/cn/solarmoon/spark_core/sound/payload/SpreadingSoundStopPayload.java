package cn.solarmoon.spark_core.sound.payload;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SpreadingSoundStopPayload(UUID uuid) implements CustomPacketPayload {

    public static final Type<SpreadingSoundStopPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "stop_spreading_sound_payload")
    );

    public static final StreamCodec<FriendlyByteBuf, SpreadingSoundStopPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SpreadingSoundStopPayload decode(@NotNull FriendlyByteBuf buffer) {
            return new SpreadingSoundStopPayload(buffer.readUUID());
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, SpreadingSoundStopPayload value) {
            buffer.writeUUID(value.uuid());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final SpreadingSoundStopPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> SpreadingSoundHelper.stopSound(context.player().level(), payload.uuid()));
    }
}
