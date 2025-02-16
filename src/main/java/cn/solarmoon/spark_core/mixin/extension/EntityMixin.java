package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.CollisionHurtData;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import cn.solarmoon.spark_core.registry.common.SyncerTypes;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder, HurtDataHolder, SkillHost, Syncer {

    @Shadow private int id;
    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);
    private CollisionHurtData data;
    private final ConcurrentHashMap<Integer, SkillInstance> allSkills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, SkillInstance> predictedSkills = new ConcurrentHashMap<>();
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
    public @NotNull SyncerType getSyncerType() {
        return SyncerTypes.getENTITY().get();
    }

    @Override
    public SyncData getSyncData() {
        return new IntSyncData(id);
    }

    @Override
    @NotNull
    public ConcurrentHashMap<Integer, SkillInstance> getAllSkills() {
        return allSkills;
    }

    @Override
    public @NotNull AtomicInteger getSkillCount() {
        return skillCount;
    }

    @Override
    public @NotNull ConcurrentHashMap<@NotNull Integer, @NotNull SkillInstance> getPredictedSkills() {
        return predictedSkills;
    }

}
