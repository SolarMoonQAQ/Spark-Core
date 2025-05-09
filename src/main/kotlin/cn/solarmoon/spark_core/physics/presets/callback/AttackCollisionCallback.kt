package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity

/**
 * AttackCollisionCallback 接口定义了攻击碰撞回调的标准流程。
 * 推荐实现 onProcessed(o1, o2, manifoldId) 版本，确保 manifoldId 贯通。
 */
interface AttackCollisionCallback: CollisionCallback {

    val attackSystem: AttackSystem

    /**
     * 攻击前回调，判断是否首次攻击。
     * @param isFirst 是否首次攻击
     * @param attacker 攻击者
     * @param target 目标
     * @param aBody 攻击者物理对象
     * @param bBody 目标物理对象
     * @param manifoldId 碰撞点ID
     */
    fun preAttack(
        isFirst: Boolean,
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ) {
    }

    /**
     * 执行攻击回调，返回是否成功。
     * @param attacker 攻击者
     * @param target 目标
     * @param aBody 攻击者物理对象
     * @param bBody 目标物理对象
     * @param manifoldId 碰撞点ID
     * @return 是否成功
     */
    fun doAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ): Boolean = true

    /**
     * 攻击后回调。
     * @param attacker 攻击者
     * @param target 目标
     * @param aBody 攻击者物理对象
     * @param bBody 目标物理对象
     * @param manifoldId 碰撞点ID
     */
    fun postAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ) {
    }

    override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
        val attacker = o1.owner as? Entity ?: return
        (o2.owner as? Entity)?.apply {
            attackSystem.customAttack(this) {
                this@apply.pushHurtData(CollisionHurtData(o1, o2, manifoldId))
                preAttack(attackSystem.attackedEntities.isEmpty(), attacker, this@apply, o1, o2, manifoldId)
                if (!doAttack(attacker, this@apply, o1, o2, manifoldId)) return@customAttack false
                postAttack(attacker, this@apply, o1, o2, manifoldId)
                true
            }
        }
    }
}