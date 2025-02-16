package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.entity.EntityFunction;
import cn.solarmoon.spark_core.molang.core.util.MolangUtils;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;

public class BiomeHasAllTags extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        Entity entity = context.entity().entity();
        Holder<Biome> biome = entity.level().getBiome(entity.blockPosition());

        for (int i = 0; i < arguments.size(); i++) {
            ResourceLocation tagId = MolangUtils.parseResourceLocation(context.entity(), arguments.getAsString(context, i));
            if (tagId == null) {
                return null;
            }
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, tagId);
            if (!biome.is(tag)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 1;
    }
}
