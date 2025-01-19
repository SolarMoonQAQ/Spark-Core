package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.AttackedData;
import cn.solarmoon.spark_core.entity.attack.IAttackedDataPusher;
import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import cn.solarmoon.spark_core.event.SkillControllerRegisterEvent;
import cn.solarmoon.spark_core.skill.ISkillControllerHolder;
import cn.solarmoon.spark_core.skill.ISkillControllerStateMachineHolder;
import cn.solarmoon.spark_core.skill.SkillController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nsk.kstatemachine.statemachine.StateMachine;

import java.util.*;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder, ISkillControllerStateMachineHolder, ISkillControllerHolder<Entity>, IAttackedDataPusher {

    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);
    private final LinkedHashMap<String, SkillController<Entity>> skillControllers = new LinkedHashMap<>();
    private SkillController<Entity> controller;
    private StateMachine sm;
    private AttackedData data = null;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(EntityType entityType, Level level, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new SkillControllerRegisterEvent.Entity(entity, skillControllers));
    }

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

    @Override
    public StateMachine getStateMachine() {
        return sm;
    }

    @Override
    public SkillController<Entity> getSkillController() {
        return controller;
    }

    @Override
    public void setSkillController(SkillController<Entity> entitySkillController) {
        controller = entitySkillController;
    }

    @Override
    public void setStateMachine(@NotNull StateMachine stateMachine) {
        sm = stateMachine;
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull SkillController<Entity>> getAllSkillControllers() {
        return skillControllers;
    }

    @Override
    public @Nullable AttackedData getData() {
        return data;
    }

    @Override
    public void setData(@Nullable AttackedData attackedData) {
        data = attackedData;
    }
}
