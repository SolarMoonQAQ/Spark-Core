package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.event.HitPhysicsEvent
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.common.NeoForge
import org.joml.Vector3f
import com.jme3.math.Vector3f as JmeVector3f

/**
 * 封装受击事件的详细信息。
 */
data class HitData(
    val hitEntity: Entity, // 受击实体
    val attackerEntity: Entity?, // 攻击者实体 (如果可识别)
    val hitPointWorld: Vector3f, // 碰撞点 (世界坐标)
    val hitNormalWorld: Vector3f, // 碰撞法线 (世界坐标，指向受击实体内部)
    val impulse: Float, // 碰撞冲量大小
    val hitBoneName: String? = null, // 受击骨骼名称（可选）
    val hitType: String? = null, // 受击类型（如 slash、blunt、pierce）
    val direction: Vector3f? = null, // 新增：受击相对方向（单位向量，世界坐标/本地坐标，具体视需求）
    val intensity: Float? = null // 新增：受击强度（可按 impulse 归一化或直接用 impulse）
)

/**
 * 处理实体受击时的精确碰撞回调。
 *
 * 负责检测有效的受击事件，提取碰撞信息，并触发相应的受击事件。
 */
interface HitReactionCollisionCallback : AttackCollisionCallback {

    override val attackSystem: AttackSystem
        get() = AttackSystem()

    // 新实现：manifoldId 版本的 onProcessed，确保物理上下文贯通
    override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
        super.onProcessed(o1, o2, manifoldId)
        val entity1 = o1.owner as? Entity
        val entity2 = o2.owner as? Entity
        if (entity1 == null || entity2 == null) return
        // 正确用法：直接用 ManifoldPoints 的静态方法获取碰撞信息
        val hitPointWorldJme = JmeVector3f()
        val hitNormalWorldJme = JmeVector3f()
        ManifoldPoints.getPositionWorldOnB(manifoldId, hitPointWorldJme)
        ManifoldPoints.getNormalWorldOnB(manifoldId, hitNormalWorldJme)
        val impulse = ManifoldPoints.getAppliedImpulse(manifoldId)
        // 转换为 JOML Vector3f
        val hitPointWorldJoml = Vector3f(hitPointWorldJme.x, hitPointWorldJme.y, hitPointWorldJme.z)
        val hitNormalWorldJoml = Vector3f(hitNormalWorldJme.x, hitNormalWorldJme.y, hitNormalWorldJme.z)
        // 新增：计算方向与强度
        val direction = hitNormalWorldJoml.normalize(Vector3f()) // 以法线方向为相对方向（如需更复杂可调整）
        val intensity = impulse // 直接使用 impulse，可后续归一化
        val hitData = HitData(
            hitEntity = entity1,
            attackerEntity = entity2,
            hitPointWorld = hitPointWorldJoml,
            hitNormalWorld = hitNormalWorldJoml,
            impulse = impulse,
            direction = direction,
            intensity = intensity
        )
        NeoForge.EVENT_BUS.post(HitPhysicsEvent.Process(hitData))
    }
}