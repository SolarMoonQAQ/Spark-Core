package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.CollisionHurtData;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import cn.solarmoon.spark_core.registry.common.SyncerTypes;
import cn.solarmoon.spark_core.skill.SkillGroup;
import cn.solarmoon.spark_core.skill.SkillHost;
import cn.solarmoon.spark_core.skill.SkillInstance;
import cn.solarmoon.spark_core.sync.IntSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder, HurtDataHolder, SkillHost, Syncer {

    @Shadow private int id;
    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);
    private CollisionHurtData data;
    private final LinkedHashMap<ResourceLocation, SkillGroup> skillGroups = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, SkillInstance> allSkills = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, SkillInstance> predictedSkills = new LinkedHashMap<>();
    private SkillGroup skillGroup;
    private final AtomicInteger skillCount = new AtomicInteger();

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }


    @Override
    public void pushHurtData(CollisionHurtData data) {
        this.data = data;
    }

    @Override
    public CollisionHurtData getHurtData() {
        return data;
    }

    @Override
    public @NotNull LinkedHashMap<@NotNull ResourceLocation, @NotNull SkillGroup> getSkillGroups() {
        return skillGroups;
    }

    @Override
    public @Nullable SkillGroup getActiveSkillGroup() {
        return skillGroup;
    }

    @Override
    public void setActiveSkillGroup(@Nullable SkillGroup skillGroup) {
        this.skillGroup = skillGroup;
    }

    @Override
    public @NotNull SyncerType getSyncerType() {
        return SyncerTypes.getENTITY().get();
    }

    @Override
    public SyncData getSyncData() {
        return new IntSyncData(id);
    }

    @Override
    @NotNull
    public LinkedHashMap<Integer, SkillInstance> getAllSkills() {
        return allSkills;
    }

    @Override
    public @NotNull AtomicInteger getSkillCount() {
        return skillCount;
    }

    @Override
    public @NotNull Map<@NotNull Integer, @NotNull SkillInstance> getPredictedSkills() {
        return predictedSkills;
    }

}
