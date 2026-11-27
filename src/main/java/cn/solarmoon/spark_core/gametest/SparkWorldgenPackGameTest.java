package cn.solarmoon.spark_core.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Verifies that a structure in the builtin Spark pack reaches Minecraft's template manager. */
@GameTestHolder("spark_core")
@PrefixGameTestTemplate(false)
public class SparkWorldgenPackGameTest {

    @GameTest(template = "empty_platform", batch = "sparkCoreWorldgen", timeoutTicks = 100)
    public static void builtinSparkStructureLoadsAndPlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("spark_core", "worldgen_probe");
        StructureTemplate template = level.getStructureManager().get(id)
                .orElseThrow(() -> new AssertionError("Builtin Spark structure was not loaded: " + id));

        helper.assertTrue(template.getSize().equals(new Vec3i(3, 2, 3)),
                "Probe template should be 3x2x3, got " + template.getSize());

        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        boolean placed = template.placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings(),
                level.getRandom(),
                2
        );
        helper.assertTrue(placed, "Probe structure should place into the test world");

        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertTrue(level.getBlockState(origin.offset(x, 0, z)).is(Blocks.IRON_BLOCK),
                        "Expected iron block at probe base offset " + x + ",0," + z);
            }
        }
        helper.assertTrue(level.getBlockState(origin.above(1).offset(1, 0, 1)).is(Blocks.BEACON),
                "Expected beacon above the center iron block");
        helper.succeed();
    }
}
