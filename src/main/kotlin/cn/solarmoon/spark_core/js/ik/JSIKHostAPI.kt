package cn.solarmoon.spark_core.js.ik

// 如果需要，导入你的网络处理器
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.sync.RequestIKComponentChangePayload
import cn.solarmoon.spark_core.ik.sync.IKSyncTargetPayload
import cn.solarmoon.spark_core.js.extension.JSEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor


/*
 * 为 IEntityAnimatable（实体）上的 IK 组件提供 JS 绑定。
 * 此类可能附加到 JSEntity 上，如果实体是 IEntityAnimatable 的实例。
 * 注意：此类中的方法在 JS 调用时运行在客户端。若需执行服务器端操作，请使用数据包。
 */
class JSIKHostAPI(private val jsEntity: JSEntity) { // 假设通过 JSEntity 包装器访问

    // 安全获取宿主并进行类型转换的辅助方法
    private fun getHost(): IEntityAnimatable<*>? {
        val entity = jsEntity.entity
        return entity as IEntityAnimatable<*>
    }

    // 安全获取宿主作为 Entity 的辅助方法
    private fun getHostEntity(): Entity? {
        // 确保 JSEntity 内的实体引用是有效的
        return jsEntity.entity as Entity?
    }

    /**
     * 向服务器请求为此实体添加一个 IK 组件。
     */
    fun addIKComponent(componentTypeIdStr: String) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS addIKComponent: 无法找到 JSEntity 对应的实体。")
            return
        }
        // 此处无需检查 getHost()，服务器将验证它是否为 IEntityAnimatable<*>

        val typeId = ResourceLocation.tryParse(componentTypeIdStr)
        if (typeId == null) {
            SparkCore.LOGGER.warn("JS addIKComponent: '$componentTypeIdStr' 不是一个有效的 ResourceLocation 字符串。")
            return
        }

        // 这段代码在 JS 调用时运行在客户端。
        // 它需要向服务器发送一个请求。
        val packet = RequestIKComponentChangePayload(hostEntity.id, typeId, true) // true 表示添加

        PacketDistributor.sendToServer(packet) // 使用你实际的网络发送逻辑
        SparkCore.LOGGER.info("[客户端 JS] 请求为实体 ${hostEntity.id} 添加 IK 组件 $typeId") // 占位符日志
    }

    /**
     * 向服务器请求从此实体移除一个 IK 组件。
     */
    fun removeIKComponent(componentTypeIdStr: String) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS removeIKComponent: 无法找到 JSEntity 对应的实体。")
            return
        }

        val typeId = ResourceLocation.tryParse(componentTypeIdStr)
        if (typeId == null) {
            SparkCore.LOGGER.warn("JS removeIKComponent: '$componentTypeIdStr' 不是一个有效的 ResourceLocation 字符串。")
            return
        }

        // 这段代码在 JS 调用时运行在客户端。
        // 它需要向服务器发送一个请求。
        val packet = RequestIKComponentChangePayload(hostEntity.id, typeId, false) // false 表示移除
        PacketDistributor.sendToServer(packet) // 使用你实际的网络发送逻辑
        SparkCore.LOGGER.info("[客户端 JS] 请求为实体 ${hostEntity.id} 移除 IK 组件 $typeId") // 占位符日志
    }

    /**
     * 向服务器请求设置此实体上特定 IK 链的目标位置。
     * 实际更新发生在服务器处理请求并同步回来之后。
     */
    fun setIKTarget(chainName: String, x: Double, y: Double, z: Double) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS setIKTarget: 无法找到 JSEntity 对应的实体。")
            return
        }

        // 向服务器发送请求
        val targetPos = Vec3(x, y, z)
        // 假设 IKSyncTargetPayload 存在并且接受 (entityId, chainName, targetPosition)
        val packet = IKSyncTargetPayload(hostEntity.id, chainName, targetPos)
        PacketDistributor.sendToServer(packet)
        SparkCore.LOGGER.info("[客户端 JS] 请求为实体 ${hostEntity.id} 的链 '$chainName' 设置 IK 目标位置至 $targetPos")

        // 可选：客户端预测（立即在本地应用以获得更平滑的视觉效果）
        // 这要求客户端实体实际上是 IEntityAnimatable<*> 并且拥有目标映射
        // val host = getHost()
        // host?.ikTargetPositions?.put(chainName, targetPos) // 如果需要，在本地应用
    }

    /**
     * 向服务器请求清除此实体上特定 IK 链的目标位置。
     * 实际更新发生在服务器处理请求并同步回来之后。
     */
    fun clearIKTarget(chainName: String) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS clearIKTarget: 无法找到 JSEntity 对应的实体。")
            return
        }
        // 向服务器发送请求（使用相同的同步数据包，但目标为 null 或可选）
        // 假设 IKSyncTargetPayload 处理 null Vec3 以进行清除，或有单独的机制
        val packet = IKSyncTargetPayload(hostEntity.id, chainName, null) // 传递 null Vec3 以表示清除
        PacketDistributor.sendToServer(packet)
        SparkCore.LOGGER.info("[客户端 JS] 请求清空实体 ${hostEntity.id} 链 '$chainName' 的 IK 目标位置")

        // 可选：客户端预测
         val host = getHost()
         host?.ikTargetPositions?.remove(chainName) // 如果需要，在本地应用
    }
}