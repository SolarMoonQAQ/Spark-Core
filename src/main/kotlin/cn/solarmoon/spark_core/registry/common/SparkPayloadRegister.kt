package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import cn.solarmoon.spark_core.skill.SkillGroupControlPayload
import cn.solarmoon.spark_core.skill.SkillInstancePredictPayload
import cn.solarmoon.spark_core.skill.SkillInstancePredictSyncPayload
import cn.solarmoon.spark_core.skill.SkillInstanceSyncPayload
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.BehaviorNodePayload
import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShakePayload
import cn.solarmoon.spark_core.visual_effect.shadow.ShadowPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent


object SparkPayloadRegister {

    private fun net(event: RegisterPayloadHandlersEvent) {
        val anim = event.registrar("animation")
        anim.configurationToClient(ModelDataPayload.TYPE, ModelDataPayload.STREAM_CODEC, ModelDataPayload::handleInClient)
        anim.configurationToServer(ModelDataSendingTask.Return.TYPE, ModelDataSendingTask.Return.STREAM_CODEC, ModelDataSendingTask.Return::onAct)
        anim.playBidirectional(TypedAnimPayload.TYPE, TypedAnimPayload.STREAM_CODEC, TypedAnimPayload::handleBothSide)
        anim.playToClient(AnimSpeedChangePayload.TYPE, AnimSpeedChangePayload.STREAM_CODEC, AnimSpeedChangePayload::handleInClient)

        val visual = event.registrar("visual_effect")
        visual.playToClient(ShadowPayload.TYPE, ShadowPayload.STREAM_CODEC, ShadowPayload::handleInClient)
        visual.playToClient(CameraShakePayload.TYPE, CameraShakePayload.STREAM_CODEC, CameraShakePayload::handleInClient)

        val skill = event.registrar("skill")
        skill.playBidirectional(SkillGroupControlPayload.TYPE, SkillGroupControlPayload.STREAM_CODEC, SkillGroupControlPayload::handleInBothSide)
        skill.playBidirectional(BehaviorNodePayload.TYPE, BehaviorNodePayload.STREAM_CODEC, BehaviorNodePayload::handleInBothSide)
        skill.playToServer(SkillInstancePredictPayload.TYPE, SkillInstancePredictPayload.STREAM_CODEC, SkillInstancePredictPayload::handleInServer)
        skill.playToClient(SkillInstancePredictSyncPayload.TYPE, SkillInstancePredictSyncPayload.STREAM_CODEC, SkillInstancePredictSyncPayload::handleInClient)
        skill.playToClient(SkillInstanceSyncPayload.TYPE, SkillInstanceSyncPayload.STREAM_CODEC, SkillInstanceSyncPayload::handleInClient)
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