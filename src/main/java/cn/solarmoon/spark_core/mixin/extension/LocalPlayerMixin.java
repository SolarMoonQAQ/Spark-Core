package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.animation.presets.PlayerStateAnimMachine;
import cn.solarmoon.spark_core.entity.player.ILocalPlayerPatch;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ru.nsk.kstatemachine.statemachine.StateMachine;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer implements ILocalPlayerPatch {

    @Shadow public Input input;
    private final LocalPlayer player = (LocalPlayer) (Object) this;
    @Unique
    private Input _1_21_1_neoforge$input0 = input;

    public LocalPlayerMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Override
    public @NotNull Input getSavedInput() {
        return _1_21_1_neoforge$input0;
    }

    @Override
    public void setSavedInput(@NotNull Input input) {
        this._1_21_1_neoforge$input0 = input;
    }
}
