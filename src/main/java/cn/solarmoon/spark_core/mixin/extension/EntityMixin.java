package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.AttackedData;
import cn.solarmoon.spark_core.entity.attack.IAttackedDataPusher;
import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import cn.solarmoon.spark_core.event.SkillControllerRegisterEvent;
import cn.solarmoon.spark_core.skill.controller.ISkillControllerHolder;
import cn.solarmoon.spark_core.skill.controller.ISkillControllerStateMachineHolder;
import cn.solarmoon.spark_core.skill.controller.SkillController;
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
    private final LinkedHashMap<String, SkillController<? extends Entity>> skillControllers = new LinkedHashMap<>();
    private SkillController<? extends Entity> controller;
    private StateMachine sm;
    private AttackedData data = null;

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

    @Override
    public StateMachine getStateMachine() {
        return sm;
    }

    @Override
    public @Nullable SkillController<? extends Entity> getSkillController() {
        return controller;
    }

    @Override
    public void setSkillController(@Nullable SkillController<? extends Entity> skillController) {
        controller = skillController;
    }

    @Override
    public void setStateMachine(@NotNull StateMachine stateMachine) {
        sm = stateMachine;
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull SkillController<? extends Entity>> getAllSkillControllers() {
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
