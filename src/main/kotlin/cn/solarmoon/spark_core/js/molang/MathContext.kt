package cn.solarmoon.spark_core.js.molang

import org.graalvm.polyglot.HostAccess

class MathContext() {
    @HostAccess.Export
    @JvmField
    val pi = Math.PI

    @HostAccess.Export
    fun sin(value: Double) = Math.sin(Math.toRadians(value))

    @HostAccess.Export
    fun cos(value: Double) = Math.cos(Math.toRadians(value))

    @HostAccess.Export
    fun tan(value: Double) = Math.tan(Math.toRadians(value))
    @HostAccess.Export
    fun asin(value: Double) = Math.toDegrees(Math.asin(value))

    @HostAccess.Export
    fun acos(value: Double) = Math.toDegrees(Math.acos(value))

    @HostAccess.Export
    fun atan(value: Double) = Math.toDegrees(Math.atan(value))

    @HostAccess.Export
    fun atan2(y: Double, x: Double) = Math.toDegrees(Math.atan2(y, x))

    @HostAccess.Export
    fun exp(value: Double) = Math.exp(value)

    @HostAccess.Export
    fun log(value: Double) = Math.log(value)

    @HostAccess.Export
    fun pow(base: Double, exponent: Double) = Math.pow(base, exponent)

    @HostAccess.Export
    fun sqrt(value: Double) = Math.sqrt(value)

    @HostAccess.Export
    fun abs(value: Double) = Math.abs(value)

    @HostAccess.Export
    fun floor(value: Double) = Math.floor(value)

    @HostAccess.Export
    fun ceil(value: Double) = Math.ceil(value)

    @HostAccess.Export
    fun round(value: Double) = Math.round(value)

    @HostAccess.Export
    fun mod(value1: Double, value2: Double) = value1 % value2

    @HostAccess.Export
    fun sign(value: Double) = Math.signum(value)

    @HostAccess.Export
    fun min(value1: Double, value2: Double) = Math.min(value1, value2)

    @HostAccess.Export
    fun max(value1: Double, value2: Double) = Math.max(value1, value2)

    @HostAccess.Export
    fun clamp(value: Double, min: Double, max: Double) = value.coerceIn(min, max)

    @HostAccess.Export
    fun lerp(start: Double, end: Double, alpha: Double) = start + alpha * (end - start)

}