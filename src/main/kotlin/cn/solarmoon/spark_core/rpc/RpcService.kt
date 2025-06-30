package cn.solarmoon.spark_core.rpc

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

interface RpcService {
    fun callMethod(methodName: String, params: Map<String, String>, level: ServerLevel, player: ServerPlayer): Any?
}