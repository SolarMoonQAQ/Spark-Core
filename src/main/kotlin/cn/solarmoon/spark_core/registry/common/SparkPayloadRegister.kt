package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.sync.AnimShouldTurnPayload
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.animation.sync.AnimationDataSendingTask
import cn.solarmoon.spark_core.animation.sync.AnimationDataSyncPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.animation.sync.ModelIndexSyncPayload
import cn.solarmoon.spark_core.animation.sync.OAnimationSetSyncPayload
import cn.solarmoon.spark_core.animation.sync.TypedAnimBlendPayload
import cn.solarmoon.spark_core.animation.sync.TypedAnimPlayPayload
import cn.solarmoon.spark_core.animation.texture.sync.TextureDataSendingTask
import cn.solarmoon.spark_core.animation.texture.sync.TextureDataSyncPayload
import cn.solarmoon.spark_core.entity.EntityMovingPayload
import cn.solarmoon.spark_core.ik.sync.IKComponentSyncPayload
import cn.solarmoon.spark_core.ik.sync.IKDataPayload
import cn.solarmoon.spark_core.ik.sync.IKDataSendingTask
import cn.solarmoon.spark_core.ik.sync.IKSyncTargetPayload
import cn.solarmoon.spark_core.ik.sync.RequestIKComponentChangePayload
import cn.solarmoon.spark_core.ik.sync.RequestSetIKTargetPayload
import cn.solarmoon.spark_core.js.sync.JSIncrementalSyncS2CPacket
import cn.solarmoon.spark_core.js.sync.JSPayload
import cn.solarmoon.spark_core.js.sync.JSSendingTask
import cn.solarmoon.spark_core.js.sync.JSTaskPayload
import cn.solarmoon.spark_core.js.sync.JSScriptDataSendingTask
import cn.solarmoon.spark_core.js.sync.JSScriptDataSyncPayload
import cn.solarmoon.spark_core.physics.sync.PhysicsCollisionObjectSyncPayload
import cn.solarmoon.spark_core.physics.sync.AddCollisionCallbackPayload
import cn.solarmoon.spark_core.physics.sync.AttackSystemSyncPayload
import cn.solarmoon.spark_core.resource.payload.registry.DynamicRegistrySyncS2CPacket
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


object SparkPayloadRegister {

    private fun net(event: RegisterPayloadHandlersEvent) {
        val anim = event.registrar("animation")
        anim.configurationToClient(ModelDataPayload.TYPE, ModelDataPayload.STREAM_CODEC, ModelDataPayload::handleInClient)
        anim.configurationToServer(ModelDataSendingTask.Return.TYPE, ModelDataSendingTask.Return.STREAM_CODEC, ModelDataSendingTask.Return::onAct)
        anim.playBidirectional(TypedAnimPlayPayload.TYPE, TypedAnimPlayPayload.STREAM_CODEC, TypedAnimPlayPayload::handleBothSide)
        anim.playBidirectional(TypedAnimBlendPayload.TYPE, TypedAnimBlendPayload.STREAM_CODEC, TypedAnimBlendPayload::handleBothSide)
        anim.playToClient(AnimSpeedChangePayload.TYPE, AnimSpeedChangePayload.STREAM_CODEC, AnimSpeedChangePayload::handleInClient)
        anim.playToClient(ModelIndexSyncPayload.TYPE, ModelIndexSyncPayload.STREAM_CODEC, ModelIndexSyncPayload::handleInClient)
        anim.playToClient(AnimShouldTurnPayload.TYPE, AnimShouldTurnPayload.STREAM_CODEC, AnimShouldTurnPayload::handleInClient)
        anim.configurationToClient(AnimationDataSyncPayload.TYPE, AnimationDataSyncPayload.STREAM_CODEC, AnimationDataSyncPayload::handleInClient)
        anim.configurationToServer(AnimationDataSendingTask.AckPayload.TYPE, AnimationDataSendingTask.AckPayload.STREAM_CODEC, AnimationDataSendingTask.AckPayload::handleOnServer)
        anim.playToClient(OAnimationSetSyncPayload.TYPE, OAnimationSetSyncPayload.STREAM_CODEC, OAnimationSetSyncPayload::handleInClient)
        // 纹理全量同步
        anim.configurationToClient(TextureDataSyncPayload.TYPE, TextureDataSyncPayload.STREAM_CODEC, TextureDataSyncPayload::handleInClient)
        anim.configurationToServer(TextureDataSendingTask.AckPayload.TYPE, TextureDataSendingTask.AckPayload.STREAM_CODEC, TextureDataSendingTask.AckPayload::handleOnServer)


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
        js.playToClient(JSIncrementalSyncS2CPacket.TYPE, JSIncrementalSyncS2CPacket.STREAM_CODEC, JSIncrementalSyncS2CPacket::handleInClient)

        // JS脚本全量同步
        js.configurationToClient(JSScriptDataSyncPayload.TYPE, JSScriptDataSyncPayload.STREAM_CODEC, JSScriptDataSyncPayload::handleInClient)
        js.configurationToServer(JSScriptDataSendingTask.AckPayload.TYPE, JSScriptDataSendingTask.AckPayload.STREAM_CODEC, JSScriptDataSendingTask.AckPayload::handleOnServer)

        val ik = event.registrar("ik")
        ik.configurationToClient(IKDataPayload.TYPE, IKDataPayload.STREAM_CODEC, IKDataPayload::handleInClient)
        ik.configurationToServer(IKDataSendingTask.Return.TYPE, IKDataSendingTask.Return.STREAM_CODEC, IKDataSendingTask.Return::onAct)
        ik.playBidirectional(IKSyncTargetPayload.TYPE, IKSyncTargetPayload.STREAM_CODEC, IKSyncTargetPayload::handleInBothSide)
        ik.playToClient(IKComponentSyncPayload.TYPE, IKComponentSyncPayload.STREAM_CODEC, IKComponentSyncPayload::handleInClient)
        ik.playToServer(RequestIKComponentChangePayload.TYPE, RequestIKComponentChangePayload.STREAM_CODEC, RequestIKComponentChangePayload::handleInServer)
        ik.playToServer(RequestSetIKTargetPayload.TYPE, RequestSetIKTargetPayload.STREAM_CODEC, RequestSetIKTargetPayload::handleInServer)

        val physics = event.registrar("physics")
        physics.playToClient(PhysicsCollisionObjectSyncPayload.TYPE, PhysicsCollisionObjectSyncPayload.STREAM_CODEC, PhysicsCollisionObjectSyncPayload::handleInClient)
        physics.playToClient(AttackSystemSyncPayload.TYPE, AttackSystemSyncPayload.STREAM_CODEC, AttackSystemSyncPayload::handleInClient)
        physics.playToClient(AddCollisionCallbackPayload.TYPE, AddCollisionCallbackPayload.STREAM_CODEC, AddCollisionCallbackPayload::handleInClient)

        val resource = event.registrar("resource")
        resource.playToClient(DynamicRegistrySyncS2CPacket.TYPE, DynamicRegistrySyncS2CPacket.STREAM_CODEC, DynamicRegistrySyncS2CPacket::handleInClient)

        val rpc = event.registrar("rpc")
        rpc.playToServer(RpcPayload.TYPE, RpcPayload.STREAM_CODEC, RpcPayload::handleInServer)

        val entity = event.registrar("entity")
        entity.playBidirectional(EntityMovingPayload.TYPE, EntityMovingPayload.STREAM_CODEC, EntityMovingPayload::handleInBothSide)
    }

    private fun task(event: RegisterConfigurationTasksEvent) {
        event.register(ModelDataSendingTask())
        event.register(JSSendingTask())
        event.register(IKDataSendingTask())
        event.register(AnimationDataSendingTask())
        // 注册新的全量同步配置任务
        event.register(JSScriptDataSendingTask())
        event.register(TextureDataSendingTask())
    }

    @JvmStatic
    fun register(modBus: IEventBus) {
        modBus.addListener(::net)
        modBus.addListener(::task)
    }

}