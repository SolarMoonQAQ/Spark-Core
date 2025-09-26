package cn.solarmoon.spark_core.entity

import net.minecraft.world.entity.Entity

interface IEntityPatch {

    var isMoving: Boolean

}

var Entity.isMoving
    get() = (this as IEntityPatch).isMoving
    set(value) {
        (this as IEntityPatch).isMoving = value
    }