package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.entity.EntityFunction;
import cn.solarmoon.spark_core.molang.core.util.MolangUtils;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;

public class BiomeHasAnyTag extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IAnimatable<Entity>> context, ArgumentCollection arguments) {
        Entity entity = context.entity().getAnimatable();
        Holder<Biome> biome = entity.level().getBiome(entity.blockPosition());

        for (int i = 0; i < arguments.size(); i++) {
            ResourceLocation id = MolangUtils.parseResourceLocation(context.entity(), arguments.getAsString(context, i));
            if (id == null) {
                return null;
            }
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, id);
            if (biome.is(tag)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 1;
    }
}
