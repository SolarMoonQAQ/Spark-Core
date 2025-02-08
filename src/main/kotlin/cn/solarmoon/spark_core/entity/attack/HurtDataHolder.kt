package cn.solarmoon.spark_core.entity.attack

interface HurtDataHolder {

    val hurtData: CollisionHurtData?

    fun pushHurtData(data: CollisionHurtData?)

}