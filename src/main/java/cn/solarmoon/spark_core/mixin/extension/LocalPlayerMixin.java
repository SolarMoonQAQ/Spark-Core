package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.animation.preset_anim.IPlayerStateAnimMachineHolder;
import cn.solarmoon.spark_core.animation.preset_anim.PlayerStateAnimMachine;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nsk.kstatemachine.statemachine.StateMachine;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin implements IPlayerStateAnimMachineHolder {

    private final LocalPlayer player = (LocalPlayer) (Object) this;
    private StateMachine stateMachine;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft minecraft, ClientLevel clientLevel, ClientPacketListener connection, StatsCounter stats, ClientRecipeBook recipeBook, boolean wasShiftKeyDown, boolean wasSprinting, CallbackInfo ci) {
        stateMachine = PlayerStateAnimMachine.create(player);
    }

    @Override
    public @NotNull StateMachine getStateMachine() {
        return stateMachine;
    }

}
