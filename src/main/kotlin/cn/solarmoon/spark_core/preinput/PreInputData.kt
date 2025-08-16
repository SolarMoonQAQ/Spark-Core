package cn.solarmoon.spark_core.preinput

data class PreInputData(
    val id: String,
    internal var input: () -> Unit,
    var remain: Int = 0,
    var maxRemainTime: Int = 5,
) {

}