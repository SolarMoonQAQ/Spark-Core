package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.physics.body.ManifoldPoint
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.neoforged.bus.api.Event

/**
 * ### ��ײ���������¼�
 *
 * ���¼�����ײ�Ӵ������ʱ������ʹ��[com.jme3.bullet.collision.ManifoldPoints]������[manifoldId]��ȡ��Ҫ�ĽӴ������Ϣ
 */
abstract class PhysicsContactEvent(val manifoldId: Long): Event() {

    class Start(manifoldId: Long): PhysicsContactEvent(manifoldId)

    class Process(val o1: PhysicsCollisionObject, val o2: PhysicsCollisionObject, o1Point: ManifoldPoint, o2Point: ManifoldPoint, manifoldId: Long): PhysicsContactEvent(manifoldId)

    class End(manifoldId: Long): PhysicsContactEvent(manifoldId)

}