package cn.solarmoon.spark_core.mixin.sound;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.mixin_interface.ISoundEngineMixin;
import cn.solarmoon.spark_core.sound.ClientSpreadingSoundPlayer;
import cn.solarmoon.spark_core.sound.SoundSourcePoint;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(value = SoundEngine.class)
public abstract class SoundEngineMixin implements ISoundEngineMixin {
    @Shadow
    @Final
    private Listener listener;

    @Shadow
    @Final
    private SoundBufferLibrary soundBuffers;

    /**
     * 存放所有正在扩散的音效实例(正在播放的存放于SoundEngine中的tickingSounds中)
     */
    @Unique
    private final List<SpreadingSoundInstance> spark_core$spreadingSounds = new ArrayList<>();
    @Unique
    private final CopyOnWriteArrayList<SpreadingSoundInstance> spark_core$spreadingSoundsBuffer = new CopyOnWriteArrayList<>();

    @Shadow
    public void play(SoundInstance soundInstance) {
    }


    @Inject(method = "tickNonPaused", at = @At("RETURN"))
    private void spark_core$tickSpreadingSounds(CallbackInfo ci) {
        Vec3 listenerPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        this.spark_core$spreadingSounds.addAll(this.spark_core$spreadingSoundsBuffer);
        this.spark_core$spreadingSoundsBuffer.clear();
        Iterator<SpreadingSoundInstance> iterator = this.spark_core$spreadingSounds.iterator();
        while (iterator.hasNext()) {
            SpreadingSoundInstance instance = iterator.next();

            // 移除已停止的实例
            if (instance.isStopped()) {
                iterator.remove();
                continue;
            }
            // 仅在"尚未开始播放"阶段由本Mixin推进tick：
            // 已开始播放后由原版SoundEngine维护tickingSounds并推进tick。
            if (!instance.isPlaying) {
                instance.tick();
            }

            // 静态声源（无ISoundSpreader）在波面全部消散后移除
            // 无ISoundSpreader意味着不会产生新波面，波面耗尽即生命周期终结
            if (instance.soundPoints.isEmpty() && instance.ISoundSpreader == null) {
                iterator.remove();
                continue;
            }

            // 先检查收听者是否在声音传播范围内
            if (!instance.isListenerInRange(listenerPos)) {
                // 不在范围内，跳过详细的波面检查
                continue;
            }

            // 检查所有已到达的声源点 TODO:检测收听者和相邻两点连线段的距离，或许更加准确？
            List<SoundSourcePoint> reachedPoints = instance.getReachedSoundPoints(listenerPos);
            if (!reachedPoints.isEmpty()) {
                for (SoundSourcePoint point : reachedPoints) {
                    instance.applySoundPoint(point);// 将抵达的波面的音效历史数据应用到声音实例

                    // 如果是第一个到达的波面且实例尚未播放，则开始播放
                    // 注意：此处不能预先 setPitch/setVolume——原版 SoundEngine.play 内部会再次调用
                    // calculatePitch/calculateVolume（已被本 Mixin 覆盖），若先把多普勒因子/距离衰减
                    // 乘入 pitch/volume 字段，会导致因子被应用两次（如多普勒因子被平方）。
                    // applySoundPoint 已将原始 pitch/volume 写入实例，play() 内覆盖后的
                    // calculatePitch/calculateVolume 会读取原始值并正确计算一次。
                    if (!instance.isPlaying && point == reachedPoints.getFirst()) {
                        this.play(instance);
                        instance.isPlaying = true;
                    }
                }
                instance.updateAABB();
            }

            if (instance.isPlaying && instance.soundPoints.isEmpty()) {
                // 所有波面都已处理完毕，可以安全移除
                iterator.remove();
            }
        }
        // 协调器清理
        if (Minecraft.getInstance().level != null) {
            ClientSpreadingSoundPlayer.cleanupStoppedInstances();
        }
    }

    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void calculatePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof SpreadingSoundInstance instance) {
            float dopplerFactor = spark_core$calculatePitch(instance);
            cir.setReturnValue(dopplerFactor);//限制多普勒因子的大小以防止极端音效
        }
    }

    @Inject(method = "calculateVolume*", at = @At("RETURN"), cancellable = true)
    private void calculateVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof SpreadingSoundInstance instance) {
            cir.setReturnValue(Math.clamp(spark_core$calculateVolume(instance), 0.0f, 1.0f));
        }
    }

    @Override
    public SoundBuffer spark_core$getSoundBuffer(ResourceLocation location) {
        var sound = soundBuffers.getCompleteBuffer(location);
        try {
            // 使用 join() 阻塞等待而非 getNow(null)，确保首次播放时异步加载的音效缓冲区已就绪
            return sound.join();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    public void spark_core$queueSpreadingSound(SpreadingSoundInstance sound) {
        this.spark_core$spreadingSoundsBuffer.add(sound);
    }

    @Unique
    public float spark_core$calculateVolume(SpreadingSoundInstance sound) {
        Vec3 sourcePos;
        if (sound.ISoundSpreader != null) {
            sourcePos = sound.ISoundSpreader.getPosition(sound.getUUID(), sound.getSoundEvent());
        } else {
            sourcePos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        }
        float distance = (float) listener.getTransform().position().distanceTo(sourcePos);
        float volume = sound.getVolume();

        // 球面声学模型（ISoundSpreader.isAttenuationIncluded == true）：
        // getVolume 已按物体真实距离完成大气吸收衰减 e^(-α·d)·(R/d)²，
        // 此处不能再按波面点到收听者的距离做二次平方衰减，否则衰减会被平方两次。
        if (sound.ISoundSpreader != null && sound.ISoundSpreader.isAttenuationIncluded()) {
            return volume;
        }

        //平方衰减
        // Square fall-off
        float rate = 1f - Math.min(distance / sound.getMaxRange(), 1f);
        return volume * rate * rate;
    }

    @Unique
    public float spark_core$calculatePitch(SpreadingSoundInstance sound) {
        Vec3 listenerSpeed;
        if (Minecraft.getInstance().getCameraEntity() instanceof Entity entity) {
            if (!entity.isPassenger())
                listenerSpeed = entity.getDeltaMovement().scale(20);
            else {
                listenerSpeed = entity.getRootVehicle().getDeltaMovement().scale(20);
            }
        } else {
            listenerSpeed = Vec3.ZERO;
        }

        Vec3 sourcePos;
        Vec3 sourceSpeed;
        if (sound.ISoundSpreader != null) {
            // 动态声源：使用当前实时状态计算多普勒，避免旧波面到达时用过时状态导致音调突变
            sourcePos = sound.ISoundSpreader.getPosition(sound.getUUID(), sound.getSoundEvent());
            sourceSpeed = sound.ISoundSpreader.getSpeed(sound.getUUID(), sound.getSoundEvent());
        } else {
            sourcePos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            sourceSpeed = sound.getSpeed();
        }
        // 源→听方向（单位向量）
        Vec3 toListener = listener.getTransform().position().subtract(sourcePos).normalize();

        float soundSpeed = SpreadingSoundInstance.getSoundSpeed(
                sourcePos.scale(0.5)
                        .add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().scale(0.5)),
                Minecraft.getInstance().level);

        // 声源速度在"源→听"方向上的投影 v_s·cosθ_s（>0 = 朝收听者接近）
        // 收听者速度在"源→听"方向上的投影 v_l·cosθ_l（>0 = 收听者朝声源运动）
        float sourceRadialSpeed = (float) sourceSpeed.dot(toListener);
        float listenerRadialSpeed = (float) listenerSpeed.dot(toListener);

        // 精确多普勒公式 f' = f · (c - v_l·cosθ_l) / (c - v_s·cosθ_s)
        // 对亚音速相对运动均成立；分母趋近 0（v_s·cosθ → c⁻）时音调急剧升高（激波堆积），由上层 clamp 限制。
        // 分母 ≤ 0 表示声源在该方向上超音速接近（收听者位于马赫锥静区），波面无法到达收听者，该声音不可闻。
        // 波面扩散模拟已天然保证静区波面晚于声源经过时刻才到达，此处仅作健壮性兜底。
        float denominator = soundSpeed - sourceRadialSpeed;
        if (denominator <= 0.0f) {
            return 0.25f * sound.getPitch();
        }
        float dopplerFactor = (soundSpeed - listenerRadialSpeed) / denominator;
        return dopplerFactor * sound.getPitch();
    }
}
