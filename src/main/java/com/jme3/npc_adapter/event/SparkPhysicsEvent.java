package com.jme3.npc_adapter.event;

import cn.solarmoon.spark_core.physics.collision.ManifoldPoint;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 物理碰撞相关事件的基类，用于处理Spark Core的物理碰撞事件。
 * 可以选择性地与CustomNPCs-Unofficial的事件系统集成。
 */
public class SparkPhysicsEvent extends Event {
    public final PhysicsCollisionObject collisionObject;
    
    public SparkPhysicsEvent(PhysicsCollisionObject collisionObject) {
        this.collisionObject = collisionObject;
    }

    /**
     * 发布事件到NeoForge事件总线
     */
    public void postToEventBus() {
        // 发布到NeoForge事件总线
        NeoForge.EVENT_BUS.post(this);
    }
    
    /**
     * 碰撞开始事件
     */
    public static class CollisionStartEvent extends SparkPhysicsEvent {
        public final PhysicsCollisionObject other;
        public final ManifoldPoint aPoint;
        public final ManifoldPoint bPoint;
        public final long manifoldId;
        
        public CollisionStartEvent(PhysicsCollisionObject collisionObject, PhysicsCollisionObject other, ManifoldPoint aPoint, ManifoldPoint bPoint, long manifoldId) {
            super(collisionObject);
            this.other = other;
            this.aPoint = aPoint;
            this.bPoint = bPoint;
            this.manifoldId = manifoldId;
        }
    }
    
    /**
     * 碰撞处理事件
     */
    public static class CollisionProcessedEvent extends SparkPhysicsEvent {
        public final PhysicsCollisionObject other;
        public final ManifoldPoint aPoint;
        public final ManifoldPoint bPoint;
        public final long manifoldId;
        
        public CollisionProcessedEvent(PhysicsCollisionObject collisionObject, PhysicsCollisionObject other, ManifoldPoint aPoint, ManifoldPoint bPoint, long manifoldId) {
            super(collisionObject);
            this.other = other;
            this.aPoint = aPoint;
            this.bPoint = bPoint;
            this.manifoldId = manifoldId;
        }
    }
    
    /**
     * 碰撞结束事件
     */
    public static class CollisionEndEvent extends SparkPhysicsEvent {
        public final PhysicsCollisionObject other;
        public final ManifoldPoint aPoint;
        public final ManifoldPoint bPoint;
        public final long manifoldId;
        
        public CollisionEndEvent(PhysicsCollisionObject collisionObject, PhysicsCollisionObject other, ManifoldPoint aPoint, ManifoldPoint bPoint, long manifoldId) {
            super(collisionObject);
            this.other = other;
            this.aPoint = aPoint;
            this.bPoint = bPoint;
            this.manifoldId = manifoldId;
        }
    }
    
    /**
     * 攻击碰撞事件
     */
    public static class AttackCollisionEvent extends SparkPhysicsEvent {
        public final Entity attacker;
        public final Entity target;
        public final PhysicsCollisionObject aBody;
        public final PhysicsCollisionObject bBody;
        public final ManifoldPoint aPoint;
        public final ManifoldPoint bPoint;

        public final long manifoldId;
        public final String phase; // "pre", "do", "post"
        
        public AttackCollisionEvent(
            Entity attacker,
            Entity target,
            PhysicsCollisionObject aBody,
            PhysicsCollisionObject bBody,
            ManifoldPoint aPoint,
            ManifoldPoint bPoint,
            long manifoldId,
            String phase
        ) {
            super(aBody);
            this.attacker = attacker;
            this.target = target;
            this.aBody = aBody;
            this.bBody = bBody;
            this.aPoint = aPoint;
            this.bPoint = bPoint;
            this.manifoldId = manifoldId;
            this.phase = phase;
        }
    }
}
