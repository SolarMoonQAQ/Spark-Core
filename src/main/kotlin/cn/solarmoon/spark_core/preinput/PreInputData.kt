package cn.solarmoon.spark_core.preinput

import cn.solarmoon.spark_core.event.OnPreInputExecuteEvent
import net.neoforged.neoforge.common.NeoForge

data class PreInputData(
    val preInput: PreInput,
    val id: String,
    internal val input: () -> Unit,
    var remain: Int = 0,
    var maxRemainTime: Int = 5,
) {

}