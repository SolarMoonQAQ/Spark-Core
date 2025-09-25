package cn.solarmoon.spark_core.physics.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.delta_sync.DiffPacket
import cn.solarmoon.spark_core.physics.component.Authority
import cn.solarmoon.spark_core.physics.component.CollisionObjectType
import cn.solarmoon.spark_core.physics.component.addCollisionComponent
import cn.solarmoon.spark_core.physics.component.shape.CollisionShapeType
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class CreatePhysicsBodyPayload(
    val id: Long,
    val name: String,
    val authority: Authority,
    val type: CollisionObjectType<*>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: CreatePhysicsBodyPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level()
                val body = payload.type.create(payload.name, payload.authority, level).apply {
                    id = payload.id
                }
                level.addCollisionComponent(body)
                level.physicsLevel.submitImmediateTask {
                    if (body.body is PhysicsRigidBody) body.body.isKinematic = true // 服务端权威的刚体在客户端中只能为运动学（不在客户端主动运动，由服务端同步运动）
                    level.physicsLevel.world.addCollisionObject(body.body)
                }
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<CreatePhysicsBodyPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "create_body"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CreatePhysicsBodyPayload::id,
            ByteBufCodecs.STRING_UTF8, CreatePhysicsBodyPayload::name,
            Authority.STREAM_CODEC, CreatePhysicsBodyPayload::authority,
            CollisionObjectType.STREAM_CODEC, CreatePhysicsBodyPayload::type,
            ::CreatePhysicsBodyPayload
        )
    }

}