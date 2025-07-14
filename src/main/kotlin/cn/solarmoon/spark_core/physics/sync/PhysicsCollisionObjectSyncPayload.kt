package cn.solarmoon.spark_core.physics.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.host.getBody
import cn.solarmoon.spark_core.physics.presets.callback.SparkCollisionCallback
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.*
import java.util.function.Function

/**
 * 用于同步物理碰撞对象的网络数据包
 *
 * @param syncerType 同步器类型
 * @param syncData 同步数据
 * @param operation 操作类型（CREATE, UPDATE, REMOVE）
 * @param collisionBoxId 碰撞盒ID
 * @param boneName 骨骼名称（仅CREATE操作需要）
 * @param size 碰撞盒尺寸（仅CREATE操作需要）
 * @param offset 碰撞盒偏移（仅CREATE操作需要）
 */
class PhysicsCollisionObjectSyncPayload(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val operation: Operation,
    val collisionBoxId: String,
    val boneName: String? = null,
    val size: Vec3? = null,
    val offset: Vec3? = null
) : CustomPacketPayload {

    /**
     * 操作类型枚举
     */
    enum class Operation {
        CREATE, UPDATE, REMOVE
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<PhysicsCollisionObjectSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "physics_collision_object_sync")
        )

        @JvmStatic
        val OPERATION_CODEC = ByteBufCodecs.INT.map(
            Function { Operation.entries[it] },
            Function { it.ordinal }
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PhysicsCollisionObjectSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, PhysicsCollisionObjectSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): PhysicsCollisionObjectSyncPayload {
                val syncerType = SyncerType.STREAM_CODEC.decode(buffer)
                val syncData = SyncData.STREAM_CODEC.decode(buffer)
                val operation = OPERATION_CODEC.decode(buffer)
                val collisionBoxId = ByteBufCodecs.STRING_UTF8.decode(buffer)
                val boneName = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buffer).orElse(null)
                val size = ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC).decode(buffer).orElse(null)
                val offset = ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC).decode(buffer).orElse(null)

                return PhysicsCollisionObjectSyncPayload(
                    syncerType, syncData, operation, collisionBoxId, boneName, size, offset
                )
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: PhysicsCollisionObjectSyncPayload) {
                SyncerType.STREAM_CODEC.encode(buffer, value.syncerType)
                SyncData.STREAM_CODEC.encode(buffer, value.syncData)
                OPERATION_CODEC.encode(buffer, value.operation)
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.collisionBoxId)
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buffer, Optional.ofNullable(value.boneName))
                ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC).encode(buffer, Optional.ofNullable(value.size))
                ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC).encode(buffer, Optional.ofNullable(value.offset))
            }
        }

        /**
         * 将Vec3转换为Vector3f
         */
        private fun Vec3.toBVector3f(): Vector3f {
            return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
        }

        @JvmStatic
        fun handleInClient(payload: PhysicsCollisionObjectSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level()
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? PhysicsHost ?: return@enqueueWork

                when (payload.operation) {
                    Operation.CREATE -> {
                        if (payload.boneName == null || payload.size == null || payload.offset == null) {
                            SparkCore.LOGGER.error("PhysicsCollisionObjectSyncPayload: Missing required parameters for CREATE operation")
                            return@enqueueWork
                        }

                        // 创建碰撞盒
                        val size = payload.size.div(2.0).toBVector3f() // 缩小一半以适应JBullet的尺寸计算方式
                        val offset = payload.offset.toBVector3f()
                        val body = PhysicsRigidBody(payload.collisionBoxId, host, BoxCollisionShape(size))

                        // 配置碰撞盒属性
                        host.bindBody(body) {
                            isContactResponse = false
                            isKinematic = true
                            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_OBJECT or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
                            setGravity(Vector3f())
                            addPhysicsTicker(MoveWithAnimatedBoneTicker(payload.boneName, offset))
                            this.setEnableSleep(false) // 'this' refers to PhysicsRigidBody (body)
                            // Ensure host is an Entity for the callback
                            val entity = host as? Entity ?: run {
                                SparkCore.LOGGER.error("PhysicsCollisionObjectSyncPayload: host cannot be cast to Entity for SparkCollisionCallback.")
                                return@bindBody // or handle error appropriately
                            }
                            this.addCollisionCallback(SparkCollisionCallback(
                                owner = entity,
                                cbName = body.name,
                                collisionBoxId = body.name
                            ))
                        }

                        SparkCore.LOGGER.info("PhysicsCollisionObjectSyncPayload: Created collision box '${payload.collisionBoxId}' bound to bone '${payload.boneName}'")
                    }
                    Operation.UPDATE -> {
                        // 获取现有的碰撞盒
                        val existingBody = host.getBody<PhysicsRigidBody>(payload.collisionBoxId)
                        if (existingBody == null) {
                            SparkCore.LOGGER.error("PhysicsCollisionObjectSyncPayload: Cannot update non-existent collision box '${payload.collisionBoxId}'")
                            return@enqueueWork
                        }

                        // 更新碰撞盒属性
                        if (payload.size != null) {
                            // 更新碰撞盒大小
                            val newSize = payload.size.div(2.0).toBVector3f() // 缩小一半以适应JBullet的尺寸计算方式
                            val newShape = BoxCollisionShape(newSize)
                            existingBody.collisionShape = newShape
                        }

                        if (payload.boneName != null && payload.offset != null) {
                            // 更新骨骼绑定和偏移量
                            val newOffset = payload.offset.toBVector3f()

                            // 移除旧的骨骼追踪器
                            existingBody.tickers.removeIf { it is MoveWithAnimatedBoneTicker }

                            // 添加新的骨骼追踪器
                            existingBody.addPhysicsTicker(MoveWithAnimatedBoneTicker(payload.boneName, newOffset))
                        } else if (payload.offset != null) {
                            // 仅更新偏移量
                            val newOffset = payload.offset.toBVector3f()
                            existingBody.tickers.filterIsInstance<MoveWithAnimatedBoneTicker>().forEach { ticker ->
                                ticker.offset.set(newOffset)
                            }
                        }

                        SparkCore.LOGGER.info("PhysicsCollisionObjectSyncPayload: Updated collision box '${payload.collisionBoxId}'")
                    }
                    Operation.REMOVE -> {
                        // 移除碰撞盒
                        host.removeBody(payload.collisionBoxId)
                        SparkCore.LOGGER.info("PhysicsCollisionObjectSyncPayload: Removed collision box '${payload.collisionBoxId}'")
                    }
                }
            }.exceptionally {
                SparkCore.LOGGER.error("PhysicsCollisionObjectSyncPayload: Error handling payload", it)
                null
            }
        }

        @JvmStatic
        fun handleInServer(payload: PhysicsCollisionObjectSyncPayload, context: IPayloadContext) {
            // 服务端接收客户端的同步请求（如果需要）
            // 暂不实现, 需要鉴权
            context.enqueueWork {
                SparkCore.LOGGER.info("PhysicsCollisionObjectSyncPayload: Received client request for operation ${payload.operation} on collision box '${payload.collisionBoxId}'")
            }
        }
    }
}
