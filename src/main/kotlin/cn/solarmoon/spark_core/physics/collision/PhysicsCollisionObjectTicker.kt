package cn.solarmoon.spark_core.physics.collision

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.level.Level

interface PhysicsCollisionObjectTicker {

    /**
     * 物理模拟推进前的回调函数，施加/清除受力等操作应在此进行
     * @param body 碰撞体对象
     * @param level 物理世界
     */
    fun prePhysicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {}

    /**
     * 物理模拟推进后的回调函数
     * @param body 碰撞体对象
     * @param level 物理世界
     */
    fun physicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {}

    fun mcTick(body: PhysicsCollisionObject, level: Level) {}

}