package cn.solarmoon.spark_core.physics.host

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.cast

interface PhysicsHost {

    val physicsLevel: PhysicsLevel

    /**
     * 绑定物理体到宿主
     * @param id 唯一标识符
     * @param body 物理体实例
     * @param allowOverride 是否允许覆盖已有Body（默认false）
     */
    fun <T: PhysicsCollisionObject> bindBody(
        id: String,
        body: T,
        allowOverride: Boolean = false
    ): T {
        physicsLevel.apply {
            submitTask {
                hostManager
                    .getOrPut(this@PhysicsHost) { ConcurrentHashMap() }
                    .compute(id) { _, existing ->
                        // 处理已存在的碰撞体
                        existing?.let { oldBody ->
                            if (!allowOverride) {
                                SparkCore.LOGGER.error("Body '$id' 已存在，启用 allowOverride 以覆盖")
                                return@compute oldBody // 返回旧值，不覆盖
                            }
                            // 允许覆盖时，先移除旧碰撞体
                            removeCollisionObject(oldBody)
                        }

                        // 添加新碰撞体到物理世界
                        addCollisionObject(body)
                        body // 存储新碰撞体
                    }
            }
        }
        return body
    }

    /**
     * 获取宿主的物理体
     * @param type 指定想获取物理体的Class对象
     * @return 如果类型匹配则返回实例，否则返回null
     */
    fun <T : PhysicsCollisionObject> getBody(id: String, type: KClass<T>): T? {
        val body = physicsLevel.hostManager[this]?.get(id)
        return if (type.isInstance(body)) type.cast(body) else null
    }

    /**
     * 获取物理宿主身上所有绑定的物理体
     */
    fun getAllBodies() = physicsLevel.hostManager[this]?.values

    /**
     * 删除并销毁对象身上的物理体
     */
    fun removeBody(id: String) {
        physicsLevel.hostManager[this]?.remove(id)?.let {
            physicsLevel.removeCollisionObject(it)
        }
    }

    fun removeAllBodies() {
        physicsLevel.hostManager[this]?.apply {
            values.forEach { physicsLevel.removeCollisionObject(it) }
            clear()
        }
    }

}