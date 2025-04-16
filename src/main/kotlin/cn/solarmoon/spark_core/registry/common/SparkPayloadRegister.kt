package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import cn.solarmoon.spark_core.ik.payload.IKComponentSyncPayload
import cn.solarmoon.spark_core.ik.payload.IKSyncTargetPayload
import cn.solarmoon.spark_core.ik.payload.RequestIKComponentChangePayload
import cn.solarmoon.spark_core.ik.payload.RequestSetIKTargetPayload
import cn.solarmoon.spark_core.js.sync.JSPayload
import cn.solarmoon.spark_core.js.sync.JSSendingTask
import cn.solarmoon.spark_core.js.sync.JSTaskPayload
import cn.solarmoon.spark_core.skill.payload.SkillPayload
import cn.solarmoon.spark_core.skill.payload.SkillPredictPayload
import cn.solarmoon.spark_core.skill.payload.SkillPredictSyncPayload
import cn.solarmoon.spark_core.skill.payload.SkillSyncPayload
import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShakePayload
import cn.solarmoon.spark_core.visual_effect.shadow.ShadowPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks


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
        skill.playToServer(SkillPredictPayload.TYPE, SkillPredictPayload.STREAM_CODEC, SkillPredictPayload::handleInServer)
        skill.playToClient(SkillPredictSyncPayload.TYPE, SkillPredictSyncPayload.STREAM_CODEC, SkillPredictSyncPayload::handleInClient)
        skill.playToClient(SkillSyncPayload.TYPE, SkillSyncPayload.STREAM_CODEC, SkillSyncPayload::handleInClient)
        skill.playBidirectional(SkillPayload.TYPE, SkillPayload.STREAM_CODEC, SkillPayload::handleInBothSide)

        val js = event.registrar("js")
        skill.playToClient(JSPayload.TYPE, JSPayload.STREAM_CODEC, JSPayload::handleInClient)
        js.configurationToClient(JSTaskPayload.TYPE, JSTaskPayload.STREAM_CODEC, JSTaskPayload::handleInClient)
        js.configurationToServer(JSSendingTask.Return.TYPE, JSSendingTask.Return.STREAM_CODEC, JSSendingTask.Return::onAct)

        // IK Payloads (Added based on diff)
        val ik = event.registrar("ik")
        ik.playToClient(IKSyncTargetPayload.TYPE, IKSyncTargetPayload.STREAM_CODEC, IKSyncTargetPayload::handleInClient)
        ik.playToClient(IKComponentSyncPayload.TYPE, IKComponentSyncPayload.STREAM_CODEC, IKComponentSyncPayload::handleInClient)
        ik.playToServer(RequestIKComponentChangePayload.TYPE, RequestIKComponentChangePayload.STREAM_CODEC, RequestIKComponentChangePayload::handleInServer)
        ik.playToServer(RequestSetIKTargetPayload.TYPE, RequestSetIKTargetPayload.STREAM_CODEC, RequestSetIKTargetPayload::handleInServer)

    // RPC Payloads (Commented out as in the diff)
//        val rpc = event.registrar("rpc")
//        rpc.playToServer(RpcPayload.TYPE, RpcPayload.STREAM_CODEC, RpcPayload::handleInServer)
    }

    private fun task(event: RegisterConfigurationTasksEvent) {
        event.register(ModelDataSendingTask())
        event.register(JSSendingTask())
    }

    @JvmStatic
    fun register(modBus: IEventBus) {
        modBus.addListener(::net)
        modBus.addListener(::task)
    }

}