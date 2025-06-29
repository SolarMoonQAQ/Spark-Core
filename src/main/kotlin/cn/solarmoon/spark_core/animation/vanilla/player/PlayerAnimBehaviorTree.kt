package cn.solarmoon.spark_core.animation.vanilla.player

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.behavior_tree.node.gate
import cn.solarmoon.spark_core.behavior_tree.node.perform
import cn.solarmoon.spark_core.behavior_tree.node.selector
import net.minecraft.client.player.LocalPlayer

class PlayerAnimBehaviorTree(private val player: LocalPlayer) {

    val base get() = selector {
        +gate(validate =  { player.isSprinting }) {
            perform {
                SparkCore.LOGGER.info("跑")
            }
        }
        +gate(validate =  { player.input.down && player.input.moveVector.length() > 0 }) {
            perform {
                SparkCore.LOGGER.info("后退")
            }
        }
        +gate(validate =  { player.input.moveVector.length() > 0 }) {
            perform {
                SparkCore.LOGGER.info("走")
            }
        }
    }

    fun create2() = selector {
        +gate(validate = { player.abilities.flying }) {
            base
        }
        +gate(validate = { player.isSwimming }) {
            base
        }
    }

}