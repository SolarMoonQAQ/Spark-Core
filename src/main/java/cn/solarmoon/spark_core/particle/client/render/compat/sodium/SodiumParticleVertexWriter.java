package cn.solarmoon.spark_core.particle.client.render.compat.sodium;

/**
 * Sodium 兼容的粒子顶点写入器。
 * <p>
 * TODO: 使用 Sodium 的 64 位对齐 scratch buffer 批量提交顶点。
 * 复用 SimpleBedrockModel 已验证的方案。
 * 目标：通过 sodium 的缓冲区绕过 Minecraft 原版 VertexConsumer 的开销。
 */
public class SodiumParticleVertexWriter {
    // TODO: Phase 5 实现
}
