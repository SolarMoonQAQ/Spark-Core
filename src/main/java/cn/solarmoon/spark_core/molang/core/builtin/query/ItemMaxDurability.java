package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.entity.LivingEntityFunction;
import cn.solarmoon.spark_core.molang.core.util.MolangUtils;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class ItemMaxDurability extends LivingEntityFunction {
    @Override
    protected Object eval(ExecutionContext<IAnimatable<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot equipmentSlot = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        LivingEntity entity = context.entity().getAnimatable();
        ItemStack itemBySlot = entity.getItemBySlot(equipmentSlot);
        return itemBySlot.getMaxDamage();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
