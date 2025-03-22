package cn.solarmoon.spark_core.animation.vanilla.player

import cn.solarmoon.spark_core.compat.first_person_model.FirstPersonModelCompat
import cn.solarmoon.spark_core.compat.real_camera.RealCameraCompat
import cn.solarmoon.spark_core.event.PlayerRenderAnimInFirstPersonEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.neoforged.neoforge.common.NeoForge

object PlayerAnimHelper {}

/**
 * 用于判断是否应当在第一人称下播放动画时渲染手部（目前只有物品）的动作
 */
fun AbstractClientPlayer.shouldRenderArmAnimInFirstPersonEvent(): PlayerRenderAnimInFirstPersonEvent {
    val isInFirstPerson = Minecraft.getInstance().options.cameraType.isFirstPerson
    val isMainCamera = Minecraft.getInstance().cameraEntity == this
    return NeoForge.EVENT_BUS.post(PlayerRenderAnimInFirstPersonEvent(this, isInFirstPerson && isMainCamera && !(RealCameraCompat.isActive() || FirstPersonModelCompat.isActive())))
}