package cn.solarmoon.spark_core.molang.core.builtin;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.event.MolangQueryRegisterEvent;
import cn.solarmoon.spark_core.molang.core.binding.ContextBinding;
import cn.solarmoon.spark_core.molang.core.builtin.query.*;
import cn.solarmoon.spark_core.molang.core.util.MolangUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModLoader;

public class QueryBinding extends ContextBinding {
    public static final QueryBinding INSTANCE = new QueryBinding();

    @SuppressWarnings("resource")
    private QueryBinding() {
        function("biome_has_all_tags", new BiomeHasAllTags());
        function("biome_has_any_tag", new BiomeHasAnyTag());
        function("relative_block_has_all_tags", new RelativeBlockHasAllTags());
        function("relative_block_has_any_tag", new RelativeBlockHasAnyTag());
        function("is_item_name_any", new ItemNameAny());
        function("equipped_item_all_tags", new EquippedItemAllTags());
        function("equipped_item_any_tag", new EquippedItemAnyTags());
        function("position", new Position());
        function("position_delta", new PositionDelta());

        function("max_durability", new ItemMaxDurability());
        function("remaining_durability", new ItemRemainingDurability());

//        var("actor_count", ctx -> ctx.getLevel().getEntityCount());
//        var("anim_time", ctx -> {
//            var anim = ctx.getAnimController().getPlayingAnim();
//            if (anim == null) return 0.0;
//            else return anim.getTime();
//        });
//        var("life_time", ctx -> ctx.geoInstance().getSeekTime() / 20.0);

        var("moon_phase", ctx -> ctx.getAnimLevel().getMoonPhase());
        var("time_of_day", ctx -> MolangUtils.normalizeTime(ctx.getAnimLevel().getDayTime()));
        var("time_stamp", ctx -> ctx.getAnimLevel().getDayTime());

        entityVar("head_x_rotation", ctx -> ctx.getAnimatable().getXRot());
        entityVar("head_y_rotation", ctx -> ctx.getAnimatable().getYRot());
        entityVar("yaw_speed", ctx -> getYawSpeed(ctx.getAnimatable(), ctx));
        entityVar("cardinal_facing_2d", ctx -> ctx.getAnimatable().getDirection().get3DDataValue());
        entityVar("distance_from_camera", ctx -> Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(ctx.getAnimatable().position()));
        entityVar("eye_target_x_rotation", ctx -> ctx.getAnimatable().getViewXRot(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
        entityVar("eye_target_y_rotation", ctx -> ctx.getAnimatable().getViewYRot(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
        entityVar("ground_speed", ctx -> getGroundSpeed(ctx.getAnimatable(), ctx));
        entityVar("modified_distance_moved", ctx -> ctx.getAnimatable().walkDist);
        entityVar("vertical_speed", ctx -> getVerticalSpeed(ctx.getAnimatable(), ctx));
        entityVar("walk_distance", ctx -> ctx.getAnimatable().moveDist);
        entityVar("has_rider", ctx -> ctx.getAnimatable().isVehicle());
        entityVar("is_first_person", ctx -> Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON);
        entityVar("is_in_water", ctx -> ctx.getAnimatable().isInWater());
        entityVar("is_in_water_or_rain", ctx -> ctx.getAnimatable().isInWaterRainOrBubble());
        entityVar("is_on_fire", ctx -> ctx.getAnimatable().isOnFire());
        entityVar("is_on_ground", ctx -> ctx.getAnimatable().onGround());
        entityVar("is_riding", ctx -> ctx.getAnimatable().isPassenger());
        entityVar("is_sneaking", ctx -> ctx.getAnimatable().onGround() && ctx.getAnimatable().getPose() == Pose.CROUCHING);
        entityVar("is_spectator", ctx -> ctx.getAnimatable().isSpectator());
        entityVar("is_sprinting", ctx -> ctx.getAnimatable().isSprinting());
        entityVar("is_swimming", ctx -> ctx.getAnimatable().isSwimming());

        livingEntityVar("body_x_rotation", ctx -> Mth.lerp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), ctx.getAnimatable().xRotO, ctx.getAnimatable().getXRot()));
        livingEntityVar("body_y_rotation", ctx -> Mth.wrapDegrees(Mth.lerp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), ctx.getAnimatable().yBodyRotO, ctx.getAnimatable().yBodyRot)));
        livingEntityVar("health", ctx -> ctx.getAnimatable().getHealth());
        livingEntityVar("max_health", ctx -> ctx.getAnimatable().getMaxHealth());
        livingEntityVar("hurt_time", ctx -> ctx.getAnimatable().hurtTime);
        livingEntityVar("is_eating", ctx -> ctx.getAnimatable().getUseItem().getUseAnimation() == UseAnim.EAT);
        livingEntityVar("is_playing_dead", ctx -> ctx.getAnimatable().isDeadOrDying());
        livingEntityVar("is_sleeping", ctx -> ctx.getAnimatable().isSleeping());
        livingEntityVar("is_using_item", ctx -> ctx.getAnimatable().isUsingItem());
        livingEntityVar("item_in_use_duration", ctx -> ctx.getAnimatable().getTicksUsingItem() / 20.0);
        livingEntityVar("item_max_use_duration", ctx -> getMaxUseDuration(ctx.getAnimatable()) / 20.0);
        livingEntityVar("item_remaining_use_duration", ctx -> ctx.getAnimatable().getUseItemRemainingTicks() / 20.0);
        livingEntityVar("equipment_count", ctx -> getEquipmentCount(ctx.getAnimatable()));

        // 为了兼容 ysm 添加的变量
        function("debug_output", new EmptyFunction());
        var("has_cape", ctx -> false);
        var("cape_flap_amount", ctx -> 0);
        mobEntityVar("is_jumping", ctx -> !ctx.getAnimatable().isPassenger() && !ctx.getAnimatable().onGround() && !ctx.getAnimatable().isInWater());

        ModLoader.postEvent(new MolangQueryRegisterEvent(this));
    }


    private static int getEquipmentCount(LivingEntity entity) {
        int count = 0;
        for (var slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }
            var stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static double getMaxUseDuration(LivingEntity player) {
        ItemStack useItem = player.getUseItem();
        if (useItem.isEmpty()) {
            return 0.0;
        } else {
            return useItem.getUseDuration(player);
        }
    }

    private static float getYawSpeed(Entity entity, IAnimatable<Entity> ctx) {
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (partialTicks == 0) partialTicks = 0.5f;// 防止除零错误，但会导致这一帧数值不正确
        return 20 * (entity.getYRot() - entity.yRotO) / partialTicks;
    }

    private static float getGroundSpeed(Entity entity, IAnimatable<Entity> ctx) {
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (partialTicks == 0) {
            Vec3 motion = entity.getDeltaMovement().scale(20f);
            return Mth.sqrt((float) (motion.x * motion.x + motion.z * motion.z));
        } else {
            double vx = 20 * (Mth.lerp(partialTicks, entity.xo, entity.getX()) - entity.xo) / partialTicks;
            double vz = 20 * (Mth.lerp(partialTicks, entity.zo, entity.getZ()) - entity.zo) / partialTicks;
            return Mth.sqrt((float) ((vx * vx) + (vz * vz)));
        }
    }

    private static float getVerticalSpeed(Entity entity, IAnimatable<Entity> ctx) {
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (partialTicks == 0) {
            Vec3 motion = entity.getDeltaMovement();
            return (float) motion.y * 20f;
        } else {
            return (float) (20 * (Mth.lerp(partialTicks, entity.yo, entity.getY()) - entity.yo) / (partialTicks));
        }
    }
}
