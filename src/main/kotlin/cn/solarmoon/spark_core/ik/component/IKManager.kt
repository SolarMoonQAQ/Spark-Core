package cn.solarmoon.spark_core.ik.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.sync.IKComponentSyncPayload
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.ConcurrentHashMap
import cn.solarmoon.spark_core.ik.sync.RequestIKComponentChangePayload
import cn.solarmoon.spark_core.ik.util.IKCoordinateTransformer

/**
 * 管理特定IEntityAnimatable的活动IK组件
 */
class IKManager(private val host: IEntityAnimatable<*>) {

    internal val activeComponents = ConcurrentHashMap<String, IKComponent>()

    /**
     * 根据类型添加并初始化IK组件
     * 返回true表示成功，false表示失败（例如模型未就绪，构建失败）
     * 此方法主要应在服务器端调用, 此处客户端调用是为了测试
     */
    fun addComponent(type: TypedIKComponent): Boolean {
        if (activeComponents.containsKey(type.chainName)) {
            return true
        }

        val model = host.modelController.originModel

        val chain = type.buildChain(host, model) ?: run {
            SparkCore.LOGGER.error("IKManager: 构建组件'${type.id}'的链失败，位于${host.animatable}.")
            return false
        }
        host.ikChains[chain.name] = chain
        val component = IKComponent(type, chain, host)
        activeComponents[type.chainName] = component
        SparkCore.LOGGER.info("IKManager: 已添加IK组件'${type.id}'（链：${type.chainName}）到${host.animatable}.")

        if (host.animLevel.isClientSide) {
            try {
                val entityHost = host as? Entity ?: throw IllegalStateException("IEntityAnimatable无法转换为Entity")
                PacketDistributor.sendToServer(RequestIKComponentChangePayload(host.id, type.id, true))
            } catch (e: IllegalStateException) {
                SparkCore.LOGGER.error("IKManager: 发送添加C->S组件同步包失败。", e)
            }
        }
        if (!host.animLevel.isClientSide) {
            try {
                val entityHost = host as? Entity ?: throw IllegalStateException("IEntityAnimatable无法转换为Entity")
                PacketDistributor.sendToPlayersTrackingEntity(entityHost, IKComponentSyncPayload(host, type, true))
            } catch (e: IllegalStateException) {
                SparkCore.LOGGER.error("IKManager: 发送S->C添加组件同步包失败。", e)
            }
        }
        return true
    }

    /**
     * 通过链名移除活动的IK组件
     * 为保证安全, 此方法暂时应在服务器端调用
     * 考虑完善后面的身份验证机制
     */
    fun removeComponent(chainName: String) {
        val removedComponent = activeComponents.remove(chainName)
        if (removedComponent != null) {
            SparkCore.LOGGER.info("IKManager: 已移除链'$chainName'的IK组件，来自${host.animatable}.")

            if (!host.animLevel.isClientSide) {
                try {
                    val entityHost = host as? Entity ?: throw IllegalStateException("IEntityAnimatable无法转换为Entity")
                    PacketDistributor.sendToPlayersTrackingEntity(entityHost, IKComponentSyncPayload(host, removedComponent.type, false))
                } catch (e: IllegalStateException) {
                    SparkCore.LOGGER.error("IKManager: 发送移除组件同步包失败。", e)
                }
            }
        }
    }

    /**
     * 准备IK目标，对相关组件执行地面检查
     * 此方法应在物理步骤之前调用（例如在主线程上）
     * 在动画/逻辑确定目标位置后调用
     *
     * @param physicsWorld 用于射线检测的物理世界实例
     */
    fun prepareTargetsForPhysics(physicsWorld: PhysicsWorld) {
        activeComponents.forEach { (chainName, component) ->
            // Get the desired target position set by the host (likely from animation)
            host.ikTargetPositions[chainName]?.let { targetVec3 ->
                // Convert Minecraft Vec3 to JME Vector3f for the component's method
                val targetJME = Vector3f(
                    targetVec3.x.toFloat(),
                    targetVec3.y.toFloat(),
                    targetVec3.z.toFloat()
                )
                try {
                    // 注意：现在我们假设ikTargetPositions中存储的是本地坐标
                    // 直接使用本地坐标更新目标位置
                    component.updateTargetLocalPosition(targetJME)

                    // 如果需要地面接触检测，则执行
                    if (component.stickToGround) {
                        // 将本地坐标转换为世界坐标进行射线检测
                        val worldTargetJME = IKCoordinateTransformer.localToWorldSpaceJME(host, targetJME)
                        component.updateGroundContact(physicsWorld, worldTargetJME)
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("IKManager: 处理组件'$chainName'目标时发生异常，位于${host.animatable}", e)
                }
            }
        }
    }


    fun getComponent(chainName: String): IKComponent? = activeComponents[chainName]
}