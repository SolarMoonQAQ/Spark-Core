package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.phys.attached_body.getBody
import cn.solarmoon.spark_core.phys.toDMatrix3
import cn.solarmoon.spark_core.phys.toDVector3
import cn.solarmoon.spark_core.phys.toQuaternionf
import cn.solarmoon.spark_core.phys.toVec3
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.visual_effect.common.trail.Trail
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import org.joml.Matrix3d
import org.ode4j.math.DVector3
import org.ode4j.ode.DBox
import org.ode4j.ode.OdeHelper
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3f
import java.awt.Color

object SkillControllerApplier {

    var rot = 0.0

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.getAllSkillControllers().forEach {
            it.baseTick()
            if (it.isAvailable()) it.tick()
        }

        if (entity is Player) {
            val geom = (entity.getBody("body")?.body?.firstGeom as? DBox)?.baseCopy() ?: return
            geom.rotation = Matrix3d().rotateXYZ(0.5, 0.5, rot++ / 10).toDMatrix3()
            SparkVisualEffects.GEOM.getRenderableBox("TEST").refresh(geom)
            if (entity.level().isClientSide) SparkVisualEffects.TRAIL.refresh("test") { Trail(2f, geom.position.toVec3().toVector3f(), geom.quaternion.toQuaternionf()) }
        }
    }

    @SubscribeEvent
    private fun onHit(event: LivingIncomingDamageEvent) {
        val entity = event.entity
        entity.getAllSkillControllers().forEach {
            if (it.isAvailable()) it.onHurt(event)
        }
    }

}