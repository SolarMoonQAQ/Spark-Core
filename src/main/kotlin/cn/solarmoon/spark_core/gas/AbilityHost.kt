package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.gas.sync.ActivateAbilityPayload
import cn.solarmoon.spark_core.gas.sync.GrantAbilityEntityPayload
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor

interface AbilityHost {

    var abilitySystemComponent: AbilitySystemComponent

    /**
     * 技能槽的赋予必须以服务端为准，因此此处需要实现对技能槽的同步（发包给客户端安上一样的槽）
     */
    fun syncGrantAbilitySpec(spec: AbilitySpec<*>) {
        if (this is Entity) {
            PacketDistributor.sendToAllPlayers(GrantAbilityEntityPayload(id, spec))
        }
    }

    /**
     * 技能槽的激活必须以服务端为准，因此此处需要实现对技能槽的激活（发包给客户端激活）
     */
    fun syncActivateAbility(handle: AbilityHandle, context: ActivationContext) {
        if (this is Entity) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, ActivateAbilityPayload(id, handle, context))
        }
    }

}