package cn.solarmoon.spark_core.molang.core.util;

import cn.solarmoon.spark_core.animation.IAnimatable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Locale;

public class MolangUtils {
    private static final HashMap<String, EquipmentSlot> SLOT_MAP;

    static {
        SLOT_MAP = new HashMap<>();
        SLOT_MAP.put("chest", EquipmentSlot.CHEST);
        SLOT_MAP.put("feet", EquipmentSlot.FEET);
        SLOT_MAP.put("head", EquipmentSlot.HEAD);
        SLOT_MAP.put("legs", EquipmentSlot.LEGS);
        SLOT_MAP.put("mainhand", EquipmentSlot.MAINHAND);
        SLOT_MAP.put("offhand", EquipmentSlot.OFFHAND);
    }

    public static float normalizeTime(long timestamp) {
        return ((float) (timestamp + 6000L) / 24000) % 1;
    }

    public static ResourceLocation parseResourceLocation(IAnimatable<?> context, String value) {
        return ResourceLocation.tryParse(value);
    }

    public static EquipmentSlot parseSlotType(IAnimatable<?> context, String value) {
        if (value == null) {
            return null;
        }
        return SLOT_MAP.get(value.toLowerCase(Locale.ENGLISH));
    }
}
