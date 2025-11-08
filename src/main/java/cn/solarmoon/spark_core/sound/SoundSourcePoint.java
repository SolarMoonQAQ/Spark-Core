package cn.solarmoon.spark_core.sound;

import net.minecraft.world.phys.Vec3;

/**
 * 声源点，封装单个声音发出点的所有相关信息
 */
public class SoundSourcePoint {
    private final Vec3 position;
    private final Vec3 speed;
    private final float pitch;
    private final float volume;
    private float spreadDistance;

    public SoundSourcePoint(Vec3 position, Vec3 speed, float pitch, float volume) {
        this.position = position;
        this.speed = speed;
        this.pitch = pitch;
        this.volume = volume;
        this.spreadDistance = 0F;
    }

    // Getter方法
    public Vec3 getPosition() { return position; }
    public Vec3 getSpeed() { return speed; }
    public float getPitch() { return pitch; }
    public float getVolume() { return volume; }
    public float getSpreadDistance() { return spreadDistance; }

    // Setter方法
    public void setSpreadDistance(float spreadDistance) { this.spreadDistance = spreadDistance; }

    /**
     * 计算该声音点与收听者的距离平方
     */
    public double distanceToSqr(Vec3 listenerPos) {
        return position.distanceToSqr(listenerPos);
    }

    /**
     * 检查声音是否已传播到收听者位置
     */
    public boolean hasReachedListener(Vec3 listenerPos) {
        return distanceToSqr(listenerPos) <= spreadDistance * spreadDistance;
    }

    @Override
    public String toString() {
        return String.format("SoundSourcePoint{pos=%s, dist=%.2f, pitch=%.2f, vol=%.2f}",
                position, spreadDistance, pitch, volume);
    }
}
