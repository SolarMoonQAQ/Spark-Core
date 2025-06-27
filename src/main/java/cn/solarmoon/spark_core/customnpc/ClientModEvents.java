package cn.solarmoon.spark_core.customnpc;


import cn.solarmoon.spark_core.SparkCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import cn.solarmoon.spark_core.animation.renderer.GeoLivingEntityRenderer;
import noppes.npcs.CustomEntities;

// kotlin编译器的类型检查问题把我折磨得够呛
@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 为 CustomNPC 实体类型自定义渲染器
        event.registerEntityRenderer(CustomEntities.entityCustomNpc, (manager) -> new GeoLivingEntityRenderer(manager, 0.5f));
        SparkCore.LOGGER.info("Registered entity renderers");
    }

}
