package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation
import cn.solarmoon.spark_core.animation.sync.SyncedAnimation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent

class CommonHitAutoAnim(
    entity: Entity,
    animatable: IEntityAnimatable<*>
): HitAutoAnim(entity, animatable, "CommonHit") {

    override fun getAllAnimNames(): Set<String> {
        return ALL_ANIM_NAMES.values.toSet()
    }

    override fun onActualHit(event: LivingDamageEvent.Post) {
        if (entity.level().isClientSide || getAnimSuffixName().isEmpty()) return
        val animName = getAnimName()
        ALL_SYNCED_ANIMS[animName]?.let {
            it.consume(animatable)
            it.syncToClient(entity.id)
        }
    }

    override fun getAnimSuffixName(): String {
        return ALL_ANIM_NAMES.entries.firstOrNull { it.key.invoke(entity) }?.value?.substringAfter("/") ?: ""
    }

    companion object {
        @JvmStatic
        val PREFIX = "CommonHit"

        @JvmStatic
        val ALL_ANIM_NAMES = buildMap<(Entity) -> Boolean, String> {
            fun add(condition: (Entity) -> Boolean, name: String) {
                put(condition, "$PREFIX/$name")
            }
            add({ it.fallDistance > it.maxFallDistance }, "landing")
        }

        @JvmStatic
        val ALL_SYNCED_ANIMS = buildMap {
            ALL_ANIM_NAMES.values.forEach {
                put(it, SyncedAnimation(MixedAnimation(it, startTransSpeed = 6f)))
            }
        }

        @JvmStatic
        fun registerAnim() {}
    }

}