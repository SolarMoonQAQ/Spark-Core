package cn.solarmoon.spark_core.ik.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.sync.IKSyncTargetPayload // 导入S2C同步负载
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.network.PacketDistributor // 导入PacketDistributor
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3 // 导入Vec3
import net.minecraft.world.entity.Entity

/**
 * 用于处理服务器上权威IK组件逻辑的中心服务。
 */
object IKService {

    /**
     * 处理添加或移除IK组件的请求（例如，来自网络数据包或命令）。
     * 执行验证并调用IKManager。
     */
    fun handleComponentChangeRequest(
        level: ServerLevel,
        requester: ServerPlayer?, // 可选：用于权限检查
        targetEntityId: Int,
        componentTypeId: ResourceLocation,
        addComponent: Boolean
    ) {
        val targetEntity = level.getEntity(targetEntityId)
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("IKService: 无法处理请求。实体 $targetEntityId 在级别 ${level.dimension().location()} 中未找到。")
            return
        }

        val host = targetEntity as? IEntityAnimatable<*> ?: run { // 使用非泛型IEntityAnimatable
            SparkCore.LOGGER.warn("IKService: 无法处理请求。实体 $targetEntityId (${targetEntity.type.descriptionId}) 不是IEntityAnimatable。")
            return
        }

        // TODO: 根据'requester'实现权限检查（如果需要）
        // 示例：if (requester == null || !requester.hasPermissions(REQUIRED_PERMISSION_LEVEL)) return

        val componentType = SparkRegistries.IK_COMPONENT_TYPE.get(componentTypeId) ?: run { // 通过.get()访问注册表
            SparkCore.LOGGER.warn("IKService: 无法处理请求。未知的IKComponentType ID: $componentTypeId")
            return
        }

        // 调用适当的IKManager方法（处理同步数据包发送）
        if (addComponent) {
            host.ikManager.addComponent(componentType)
        } else {
            // 确保移除使用正确的标识符（类型中的chainName）
            host.ikManager.removeComponent(componentType.chainName)
        }
    }

    /**
     * 处理为特定链设置或清除IK目标位置的请求。
     * 更新服务器端权威目标映射。
     */
    fun handleSetIKTargetRequest(
        level: ServerLevel,
        requester: ServerPlayer?, // 可选：用于权限检查
        targetEntityId: Int,
        chainName: String,
        targetPosition: Vec3? // 可空：null表示清除
    ) {
        val targetEntity = level.getEntity(targetEntityId)
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("IKService: 无法设置目标。实体 $targetEntityId 未找到。")
            return
        }
        val host = targetEntity as? IEntityAnimatable<*> ?: run {
             // 如果找到实体但不是IEntityAnimatable，则记录日志
             SparkCore.LOGGER.warn("IKService: 无法设置目标。实体 $targetEntityId (${targetEntity.type.descriptionId}) 不是IEntityAnimatable。")
             return
        }

        // TODO: 如果需要，实现权限检查

        if (targetPosition != null) {
            host.ikTargetPositions[chainName] = targetPosition // 更新服务器映射
            SparkCore.LOGGER.debug("IKService: 为链 '$chainName' 设置实体 $targetEntityId 的目标到 $targetPosition")
        } else {
            host.ikTargetPositions.remove(chainName) // 从服务器映射中清除目标
            SparkCore.LOGGER.debug("IKService: 清除链 '$chainName' 的目标，实体 $targetEntityId")
        }

        // --- 发送S2C IKSyncTargetPayload数据包以同步客户端 ---
        try {
            // 显式获取实体ID并调用主构造函数以避免潜在的解析问题
            val entityIdForPacket = (host as? Entity)?.id ?: run {
                SparkCore.LOGGER.error("IKService: 无法从IEntityAnimatable获取实体ID以用于同步数据包。")
                return // 如果无法获取有效ID，则不继续
            }
            // 直接调用主构造函数
            val syncPacket = IKSyncTargetPayload(entityIdForPacket, chainName, targetPosition)
            PacketDistributor.sendToPlayersTrackingEntity(targetEntity, syncPacket)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("IKService: 为实体 $targetEntityId、链 '$chainName' 发送IKSyncTargetPayload失败", e)
        }
    }

    // TODO: 根据需要添加其他服务器端IK逻辑方法
    // 例如，由AI、游戏事件、命令等触发的方法
    // fun addComponentFromServer(entity: Entity, typeId: ResourceLocation) { ... }
}