package com.jme3.npc_adapter.event;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.world.entity.Entity;
import noppes.npcs.api.event.CustomNPCsEvent;

/**
 * 物理碰撞相关事件的基类，用于在CustomNPCs-Unofficial的事件系统中处理Spark Core的物理碰撞事件。
 */
public class SparkPhysicsEvent extends CustomNPCsEvent {
    public final PhysicsCollisionObject collisionObject;
    
    public SparkPhysicsEvent(PhysicsCollisionObject collisionObject) {
        this.collisionObject = collisionObject;
    }
    
    /**
     * 碰撞开始事件
     */
    public static class CollisionStartEvent extends SparkPhysicsEvent {
        public final PhysicsCollisionObject other;
        public final long manifoldId;
        
        public CollisionStartEvent(PhysicsCollisionObject collisionObject, PhysicsCollisionObject other, long manifoldId) {
            super(collisionObject);
            this.other = other;
            this.manifoldId = manifoldId;
        }
    }
    
    /**
     * 碰撞处理事件
     */
    public static class CollisionProcessedEvent extends SparkPhysicsEvent {
        public final PhysicsCollisionObject other;
        public final long manifoldId;
        
        public CollisionProcessedEvent(PhysicsCollisionObject collisionObject, PhysicsCollisionObject other, long manifoldId) {
            super(collisionObject);
            this.other = other;
            this.manifoldId = manifoldId;
        }
    }
    
    /**
     * 碰撞结束事件
     */
    public static class CollisionEndEvent extends SparkPhysicsEvent {
        public final PhysicsCollisionObject other;
        public final long manifoldId;
        
        public CollisionEndEvent(PhysicsCollisionObject collisionObject, PhysicsCollisionObject other, long manifoldId) {
            super(collisionObject);
            this.other = other;
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
        public final long manifoldId;
        public final String phase; // "pre", "do", "post"
        
        public AttackCollisionEvent(
            Entity attacker,
            Entity target,
            PhysicsCollisionObject aBody,
            PhysicsCollisionObject bBody,
            long manifoldId,
            String phase
        ) {
            super(aBody);
            this.attacker = attacker;
            this.target = target;
            this.aBody = aBody;
            this.bBody = bBody;
            this.manifoldId = manifoldId;
            this.phase = phase;
        }
    }
}
