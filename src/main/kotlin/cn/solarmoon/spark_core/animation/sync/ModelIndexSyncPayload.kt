package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.callback.CustomnpcCollisionCallback
import cn.solarmoon.spark_core.physics.presets.initWithAnimatedBone
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 同步 ModelIndex 从服务端到客户端的负载, 用于切换模型
 */
data class ModelIndexSyncPayload(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val modelIndex: ModelIndex
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<ModelIndexSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_model_index")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ModelIndexSyncPayload> = StreamCodec.composite(
            SyncerType.STREAM_CODEC, { it.syncerType },
            SyncData.STREAM_CODEC, { it.syncData },
            ModelIndex.STREAM_CODEC, { it.modelIndex },
            ::ModelIndexSyncPayload
        )

        @JvmStatic
        fun handleInClient(payload: ModelIndexSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level() ?: return@enqueueWork
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? IEntityAnimatable<*> ?: return@enqueueWork
                host.modelIndex = payload.modelIndex

                host.model.bones.values.filterNot { it.name in listOf("rightItem", "leftItem") }.forEach { bone ->
                    val body = PhysicsRigidBody(bone.name, host as PhysicsHost, CompoundCollisionShape())

                    val entity = host as? Entity ?: run {
                        SparkCore.LOGGER.error("ModelIndexSyncPayload: host cannot be cast to Entity.")
                        return@forEach
                    }

                    host.bindBody(body, level.physicsLevel, true) {
                        (this.collisionShape as CompoundCollisionShape).initWithAnimatedBone(bone)
                        this.isContactResponse = false
                        this.setGravity(Vector3f.ZERO)
                        this.setEnableSleep(false)
                        this.isKinematic = true
                        this.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_OBJECT or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
                        this.addPhysicsTicker(MoveWithAnimatedBoneTicker(bone.name))
                        this.addCollisionCallback(CustomnpcCollisionCallback(
                            owner = entity,
                            cbName = body.name,
                            collisionBoxId = body.name
                        ))
                    }
                }
                SparkCore.LOGGER.info("接收到实体同步 ModelIndex 完成")
            }.exceptionally {
                context.disconnect(Component.literal("接收 ModelIndex 数据失败"))
                null
            }
        }
    }
}
