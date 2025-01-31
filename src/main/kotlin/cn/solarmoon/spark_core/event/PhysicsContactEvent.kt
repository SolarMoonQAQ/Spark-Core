package cn.solarmoon.spark_core.event

import com.jme3.bullet.collision.PhysicsCollisionObject
import net.neoforged.bus.api.Event

/**
 * ### ��ײ���������¼�
 *
 * ���¼�����ײ�Ӵ������ʱ������ʹ��[com.jme3.bullet.collision.ManifoldPoints]������[manifoldId]��ȡ��Ҫ�ĽӴ������Ϣ
 */
abstract class PhysicsContactEvent(val manifoldId: Long): Event() {

    class Start(manifoldId: Long): PhysicsContactEvent(manifoldId)

    class Process(manifoldId: Long, val o1: PhysicsCollisionObject, val o2: PhysicsCollisionObject,): PhysicsContactEvent(manifoldId)

    class End(manifoldId: Long): PhysicsContactEvent(manifoldId)

}