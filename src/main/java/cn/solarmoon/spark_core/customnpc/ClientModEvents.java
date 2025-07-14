package cn.solarmoon.spark_core.customnpc;

import cn.solarmoon.spark_core.SparkCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import cn.solarmoon.spark_core.animation.renderer.GeoLivingEntityRenderer;
import net.neoforged.fml.ModList;

// kotlin编译器的类型检查问题把我折磨得够呛
@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 只有在 CustomNPC 模组存在时才注册渲染器
        if (ModList.get().isLoaded("customnpcs")) {
            try {
                // 通过反射安全地获取 CustomEntities.entityCustomNpc
                Class<?> customEntitiesClass = Class.forName("noppes.npcs.CustomEntities");
                Object entityCustomNpc = customEntitiesClass.getField("entityCustomNpc").get(null);
                
                if (entityCustomNpc instanceof net.minecraft.world.entity.EntityType<?>) {
                    @SuppressWarnings("unchecked")
                    net.minecraft.world.entity.EntityType<?> entityType = (net.minecraft.world.entity.EntityType<?>) entityCustomNpc;
                    event.registerEntityRenderer(entityType, (manager) -> new GeoLivingEntityRenderer(manager, 0.5f));
                    SparkCore.LOGGER.info("Registered CustomNPC entity renderer");
                }
            } catch (Exception e) {
                SparkCore.LOGGER.warn("Failed to register CustomNPC entity renderer: {}", e.getMessage());
            }
        } else {
            SparkCore.LOGGER.info("CustomNPC mod not found, skipping entity renderer registration");
        }
    }

}
