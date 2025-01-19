package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import cn.solarmoon.spark_core.skill.SkillControllerStatePayload
import cn.solarmoon.spark_core.visual_effect.common.geom.RenderableGeomPayload
import cn.solarmoon.spark_core.visual_effect.common.camera_shake.CameraShakePayload
import cn.solarmoon.spark_core.visual_effect.common.shadow.ShadowPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent


object SparkPayloadRegister {

    private fun net(event: RegisterPayloadHandlersEvent) {
        val anim = event.registrar("animation")
        anim.configurationToClient(ModelDataPayload.TYPE, ModelDataPayload.STREAM_CODEC, ModelDataPayload::handleInClient)
        anim.configurationToServer(ModelDataSendingTask.Return.TYPE, ModelDataSendingTask.Return.STREAM_CODEC, ModelDataSendingTask.Return::onAct)
        anim.playBidirectional(TypedAnimPayload.TYPE, TypedAnimPayload.STREAM_CODEC, TypedAnimPayload::handleBothSide)
        anim.playToClient(SkillControllerStatePayload.TYPE, SkillControllerStatePayload.STREAM_CODEC, SkillControllerStatePayload::handleInClient)

        val box = event.registrar("box")
        box.playToClient(RenderableGeomPayload.TYPE, RenderableGeomPayload.STREAM_CODEC, RenderableGeomPayload::handleInClient)
        val visual = event.registrar("visual_effect")
        visual.playToClient(ShadowPayload.TYPE, ShadowPayload.STREAM_CODEC, ShadowPayload::handleInClient)
        visual.playToClient(CameraShakePayload.TYPE, CameraShakePayload.STREAM_CODEC, CameraShakePayload::handleInClient)
    }

    private fun task(event: RegisterConfigurationTasksEvent) {
        event.register(ModelDataSendingTask())
    }

    @JvmStatic
    fun register(modBus: IEventBus) {
        modBus.addListener(::net)
        modBus.addListener(::task)
    }

}