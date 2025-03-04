package cn.solarmoon.spark_core.rpc

import cn.solarmoon.spark_core.rpc.payload.RpcPayload
import net.neoforged.neoforge.network.PacketDistributor

object RpcClient {
    fun loadModel(path: String) {
        PacketDistributor.sendToServer(RpcPayload(
            "loadModel",
            mapOf("path" to path)
        ))
    }

    fun playAnimation(name: String, transTime: Int = 0, entityId: Int? = null) {
        val params = mutableMapOf(
            "name" to name,
            "transTime" to transTime.toString()
        )
        entityId?.let { params["entityId"] = it.toString() }
        
        PacketDistributor.sendToServer(RpcPayload(
            "playAnimation", 
            params
        ))
    }

    fun replaceStateAnimation(state: String, animation: String, entityId: Int? = null) {
        val params = mutableMapOf(
            "state" to state,
            "animation" to animation
        )
        entityId?.let { params["entityId"] = it.toString() }
        
        PacketDistributor.sendToServer(RpcPayload(
            "replaceStateAnimation",
            params
        ))
    }

    fun blendAnimations(anim1: String, anim2: String, weight: Double = 0.5, entityId: Int? = null) {
        val params = mutableMapOf(
            "anim1" to anim1,
            "anim2" to anim2,
            "weight" to weight.toString()
        )
        entityId?.let { params["entityId"] = it.toString() }
        
        PacketDistributor.sendToServer(RpcPayload(
            "blendAnimations",
            params
        ))
    }
} 