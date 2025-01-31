package cn.solarmoon.spark_core.event

import com.jme3.bullet.collision.PhysicsCollisionObject
import net.neoforged.bus.api.Event

/**
 * ### 碰撞发生处理事件
 *
 * 该事件在碰撞接触点产生时发生，使用[com.jme3.bullet.collision.ManifoldPoints]来根据[manifoldId]获取想要的接触点等信息
 */
abstract class PhysicsContactEvent(val manifoldId: Long): Event() {

    class Start(manifoldId: Long): PhysicsContactEvent(manifoldId)

    class Process(manifoldId: Long, val o1: PhysicsCollisionObject, val o2: PhysicsCollisionObject,): PhysicsContactEvent(manifoldId)

    class End(manifoldId: Long): PhysicsContactEvent(manifoldId)

}