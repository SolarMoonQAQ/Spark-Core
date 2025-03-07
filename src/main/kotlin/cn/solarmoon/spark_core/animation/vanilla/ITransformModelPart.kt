package cn.solarmoon.spark_core.animation.vanilla

import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

interface ITransformModelPart {

    var root: ModelPart?

    var pivot: Vector3f

}