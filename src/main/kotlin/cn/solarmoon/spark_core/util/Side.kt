package cn.solarmoon.spark_core.util

enum class Side {
    LEFT, RIGHT, FRONT, BACK;

    override fun toString(): String {
        return super.toString().lowercase()
    }
}