package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.animation.sync.ModelIndexSyncPayload
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import cn.solarmoon.spark_core.ik.sync.IKComponentSyncPayload
import cn.solarmoon.spark_core.ik.sync.IKDataPayload
import cn.solarmoon.spark_core.ik.sync.IKDataSendingTask
import cn.solarmoon.spark_core.ik.sync.IKSyncTargetPayload
import cn.solarmoon.spark_core.ik.sync.RequestIKComponentChangePayload
import cn.solarmoon.spark_core.ik.sync.RequestSetIKTargetPayload
import cn.solarmoon.spark_core.js.sync.JSPayload
import cn.solarmoon.spark_core.js.sync.JSSendingTask
import cn.solarmoon.spark_core.js.sync.JSTaskPayload
import cn.solarmoon.spark_core.physics.sync.PhysicsCollisionObjectSyncPayload
import cn.solarmoon.spark_core.rpc.payload.RpcPayload
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
        anim.playToClient(ModelIndexSyncPayload.TYPE, ModelIndexSyncPayload.STREAM_CODEC, ModelIndexSyncPayload::handleInClient)

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

        // IK Payloads
        val ik = event.registrar("ik")
        ik.configurationToClient(IKDataPayload.TYPE, IKDataPayload.STREAM_CODEC, IKDataPayload::handleInClient)
        ik.configurationToServer(IKDataSendingTask.Return.TYPE, IKDataSendingTask.Return.STREAM_CODEC, IKDataSendingTask.Return::onAct)
        ik.playBidirectional(IKSyncTargetPayload.TYPE, IKSyncTargetPayload.STREAM_CODEC, IKSyncTargetPayload::handleInBothSide)
        ik.playToClient(IKComponentSyncPayload.TYPE, IKComponentSyncPayload.STREAM_CODEC, IKComponentSyncPayload::handleInClient)
        ik.playToServer(RequestIKComponentChangePayload.TYPE, RequestIKComponentChangePayload.STREAM_CODEC, RequestIKComponentChangePayload::handleInServer)
        ik.playToServer(RequestSetIKTargetPayload.TYPE, RequestSetIKTargetPayload.STREAM_CODEC, RequestSetIKTargetPayload::handleInServer)

        // 注册物理碰撞对象同步数据包
        val physics = event.registrar("physics")
        physics.playToClient(
            PhysicsCollisionObjectSyncPayload.TYPE,
            PhysicsCollisionObjectSyncPayload.STREAM_CODEC,
            PhysicsCollisionObjectSyncPayload::handleInClient
        )

//        val rpc = event.registrar("rpc")
//        rpc.playToServer(RpcPayload.TYPE, RpcPayload.STREAM_CODEC, RpcPayload::handleInServer)
    }

    private fun task(event: RegisterConfigurationTasksEvent) {
        event.register(ModelDataSendingTask())
        event.register(JSSendingTask())
        event.register(IKDataSendingTask())
    }

    @JvmStatic
    fun register(modBus: IEventBus) {
        modBus.addListener(::net)
        modBus.addListener(::task)
    }

}