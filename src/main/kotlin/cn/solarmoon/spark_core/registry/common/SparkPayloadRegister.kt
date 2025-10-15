package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.animation.sync.AnimStopPayload
import cn.solarmoon.spark_core.animation.sync.ModelIndexSyncPayload
import cn.solarmoon.spark_core.gas.sync.CancelAbilityEntityPayload
import cn.solarmoon.spark_core.gas.sync.ClearAbilityEntityPayload
import cn.solarmoon.spark_core.gas.sync.EndAbilityEntityPayload
import cn.solarmoon.spark_core.gas.sync.TryActivateAbilityLocalPayload
import cn.solarmoon.spark_core.gas.sync.TryActivateAbilityEntityPayload
import cn.solarmoon.spark_core.gas.sync.GiveAbilityEntityPayload
import cn.solarmoon.spark_core.pack.sync.SparkPackagePayload
import cn.solarmoon.spark_core.pack.sync.SparkPackageReloadPayload
import cn.solarmoon.spark_core.pack.sync.SparkPackageSendingTask
import cn.solarmoon.spark_core.physics.terrain.TerrainUpdatePayload
import cn.solarmoon.spark_core.skill.payload.SkillPayload
import cn.solarmoon.spark_core.skill.payload.SkillPredictPayload
import cn.solarmoon.spark_core.skill.payload.SkillPredictSyncPayload
import cn.solarmoon.spark_core.skill.payload.SkillRejectPayload
import cn.solarmoon.spark_core.skill.payload.SkillSyncPayload
import cn.solarmoon.spark_core.sound.payload.SpreadingSoundPayload
import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShakePayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent


object SparkPayloadRegister {

    private fun net(event: RegisterPayloadHandlersEvent) {
        val anim = event.registrar("animation")
        anim.playToClient(AnimSpeedChangePayload.TYPE, AnimSpeedChangePayload.STREAM_CODEC, AnimSpeedChangePayload::handleInClient)
        anim.playToClient(ModelIndexSyncPayload.TYPE, ModelIndexSyncPayload.STREAM_CODEC, ModelIndexSyncPayload::handleInClient)
        anim.playBidirectional(AnimStopPayload.TYPE, AnimStopPayload.STREAM_CODEC, AnimStopPayload::handleBothSide)

        val visual = event.registrar("visual_effect")
        visual.playToClient(CameraShakePayload.TYPE, CameraShakePayload.STREAM_CODEC, CameraShakePayload::handleInClient)

        val skill = event.registrar("skill")
        skill.playToServer(SkillPredictPayload.TYPE, SkillPredictPayload.STREAM_CODEC, SkillPredictPayload::handleInServer)
        skill.playToClient(SkillRejectPayload.TYPE, SkillRejectPayload.STREAM_CODEC, SkillRejectPayload::handleInClient)
        skill.playToClient(SkillPredictSyncPayload.TYPE, SkillPredictSyncPayload.STREAM_CODEC, SkillPredictSyncPayload::handleInClient)
        skill.playToClient(SkillSyncPayload.TYPE, SkillSyncPayload.STREAM_CODEC, SkillSyncPayload::handleInClient)
        skill.playBidirectional(SkillPayload.TYPE, SkillPayload.STREAM_CODEC, SkillPayload::handleInBothSide)

        val physics = event.registrar("physics")
        physics.playToClient(SpreadingSoundPayload.TYPE, SpreadingSoundPayload.STREAM_CODEC, SpreadingSoundPayload::handler)
        physics.playToClient(TerrainUpdatePayload.TYPE, TerrainUpdatePayload.STREAM_CODEC, TerrainUpdatePayload::handler)

        val pack = event.registrar("package")
        pack.configurationToClient(SparkPackagePayload.TYPE, SparkPackagePayload.STREAM_CODEC, SparkPackagePayload::handleInClient)
        pack.configurationToServer(SparkPackageSendingTask.Return.TYPE, SparkPackageSendingTask.Return.STREAM_CODEC, SparkPackageSendingTask.Return::onAct)
        pack.playToClient(SparkPackageReloadPayload.TYPE, SparkPackageReloadPayload.STREAM_CODEC, SparkPackageReloadPayload::handleInClient)

        val gas = event.registrar("gas")
        gas.playToClient(GiveAbilityEntityPayload.TYPE, GiveAbilityEntityPayload.STREAM_CODEC, GiveAbilityEntityPayload::handleInClient)
        gas.playToServer(ClearAbilityEntityPayload.TYPE, ClearAbilityEntityPayload.STREAM_CODEC, ClearAbilityEntityPayload::handleInClient)
        gas.playToClient(TryActivateAbilityEntityPayload.TYPE, TryActivateAbilityEntityPayload.STREAM_CODEC, TryActivateAbilityEntityPayload::handleInClient)
        gas.playToServer(TryActivateAbilityLocalPayload.TYPE, TryActivateAbilityLocalPayload.STREAM_CODEC, TryActivateAbilityLocalPayload::handleInServer)
        gas.playToClient(CancelAbilityEntityPayload.TYPE, CancelAbilityEntityPayload.STREAM_CODEC, CancelAbilityEntityPayload::handleInClient)
        gas.playToClient(EndAbilityEntityPayload.TYPE, EndAbilityEntityPayload.STREAM_CODEC, EndAbilityEntityPayload::handleInClient)
    }

    private fun task(event: RegisterConfigurationTasksEvent) {
        event.register(SparkPackageSendingTask())
    }

    @JvmStatic
    fun register(modBus: IEventBus) {
        modBus.addListener(::net)
        modBus.addListener(::task)
    }

}
