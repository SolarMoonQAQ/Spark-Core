package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.particle.AnimatableShadowParticle
import cn.solarmoon.spark_core.particle.SpaceWarpParticle
import cn.solarmoon.spark_core.registry.common.SparkParticles
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent

object SparkParticleProviderRegister {

    private fun reg(event: RegisterParticleProvidersEvent) {
        event.registerSpecial(SparkParticles.ANIMATABLE_SHADOW.get(), AnimatableShadowParticle.Provider())
        event.registerSpecial(SparkParticles.SPACE_WARP.get(), SpaceWarpParticle.Provider())
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}