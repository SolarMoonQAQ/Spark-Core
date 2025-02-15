package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

data class MovePayload(
    val id: Int,
    val movement: Vec3,
    val operation: Operation
): CustomPacketPayload {

    enum class Operation { SET, ADD;
        companion object {
            @JvmStatic
            val STREAM_CODEC = ByteBufCodecs.INT.map({ Operation.entries[it] }, { it.ordinal })
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: MovePayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = level.getEntity(payload.id) ?: return
            when(payload.operation) {
                Operation.SET -> entity.deltaMovement = payload.movement
                Operation.ADD -> entity.deltaMovement.add(payload.movement)
            }
        }

        @JvmStatic
        fun moveEntityInClient(id: Int, movement: Vec3, operation: Operation = Operation.SET) {
            PacketDistributor.sendToAllPlayers(MovePayload(id, movement, operation))
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<MovePayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "client_move"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, MovePayload::id,
            SerializeHelper.VEC3_STREAM_CODEC, MovePayload::movement,
            Operation.STREAM_CODEC, MovePayload::operation,
            ::MovePayload
        )
    }

}