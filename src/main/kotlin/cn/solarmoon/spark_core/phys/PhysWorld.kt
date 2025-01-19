package cn.solarmoon.spark_core.phys

import org.ode4j.ode.DContactBuffer
import org.ode4j.ode.DGeom
import org.ode4j.ode.OdeHelper
import org.ode4j.ode.internal.DxWorld

class PhysWorld(val stepSize: Long): DxWorld() {

    companion object {
        const val MAX_CONTACT_AMOUNT = 64
    }

    private val lateConsumer = ArrayDeque<() -> Unit>()
    val space = OdeHelper.createHashSpace()
    val contactGroup = OdeHelper.createJointGroup()

    init {
        setGravity(0.0, -9.81, 0.0) //设置重力
        contactSurfaceLayer = 0.01 //最大陷入深度，有助于防止抖振(虽然本来似乎也没)
        erp = 0.25
        cfm = 0.00005
        autoDisableFlag = true //设置静止物体自动休眠以节约性能
        autoDisableSteps = 5
        quickStepNumIterations = 40 //设定迭代次数以提高物理计算精度
        quickStepW = 1.3
        contactMaxCorrectingVel = 20.0
    }

    fun physTick() {
        while (lateConsumer.isNotEmpty()) lateConsumer.removeLastOrNull()?.invoke()
        bodyIteration.forEach { it.physTick() }
        contactGroup.empty()
        space.collide(Any(), ::nearCallback)
        quickStep(stepSize / 1000.0)
    }

    fun nearCallback(data: Any, o1: DGeom, o2: DGeom) {
        if (!o1.collisionDetectable() || !o2.collisionDetectable()) return
        if (o1.body?.owner == o2.body?.owner) return

        val bufferSize = MAX_CONTACT_AMOUNT
        val contactBuffer = DContactBuffer(bufferSize)
        OdeHelper.collide(o1, o2, bufferSize, contactBuffer.geomBuffer)
            .takeIf { it > 0 }
            ?.let {
                if (!o2.isPassFromCollide) o1.collide(o2, contactBuffer)
                if (!o1.isPassFromCollide) o2.collide(o1, contactBuffer)

                if (contactBuffer.shouldCreateJoint()) {
                    repeat(it) { index ->
                        OdeHelper.createContactJoint(this, contactGroup, contactBuffer[index])
                            .attach(o1.body, o2.body)
                    }
                }
            }
    }

    fun laterConsume(consumer: () -> Unit) {
        lateConsumer.add(consumer)
    }

}