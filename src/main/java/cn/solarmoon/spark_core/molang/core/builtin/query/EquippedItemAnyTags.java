package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.entity.LivingEntityFunction;
import cn.solarmoon.spark_core.molang.core.util.MolangUtils;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EquippedItemAnyTags extends LivingEntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null) {
            return null;
        }

        ItemStack itemStack = context.entity().entity().getItemBySlot(slotType);
        if(itemStack.isEmpty()) {
            return false;
        }

        for (int i = 1; i < arguments.size(); i++) {
            ResourceLocation id = MolangUtils.parseResourceLocation(context.entity(), arguments.getAsString(context, i));
            if (id == null) {
                return null;
            }
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            if (itemStack.is(tag)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
