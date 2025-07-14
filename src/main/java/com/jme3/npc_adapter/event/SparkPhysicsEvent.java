package com.jme3.npc_adapter.event;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModList;

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
     * 通过反射安全地发布事件到CustomNPC事件系统
     */
    private void postToCustomNpcEventBus() {
        try {
            Class<?> npcApiClass = Class.forName("noppes.npcs.api.NpcAPI");
            Object npcApiInstance = npcApiClass.getMethod("Instance").invoke(null);
            Object eventBus = npcApiClass.getMethod("events").invoke(npcApiInstance);
            eventBus.getClass().getMethod("post", Object.class).invoke(eventBus, this);
        } catch (Exception e) {
            // 如果反射失败，忽略错误但不影响主要功能
            // 可以选择记录日志：System.err.println("Failed to post to CustomNPC event bus: " + e.getMessage());
        }
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
