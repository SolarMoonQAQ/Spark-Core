package cn.solarmoon.spark_core.gametest;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.terrain.FluidSample;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionFluidIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Spark-Core 物理感知流体的自动化 GameTest（{@code gradlew runGameTestServer}）。
 *
 * <p>覆盖 {@code docs/section_fluid_index_plan.md} §9「正确性」里可以在无头服务端上稳定观测的条目，
 * 并额外钉住两条实现决策：
 * <ul>
 *   <li><b>D3 / P0-1 独立发布通道</b>：纯流体 section 在「未激活」状态下也必须已发布流体索引；</li>
 *   <li><b>§3.4⑤ / D6</b>：跨 section 续接标记的判据是「柱顶局部 y == 15 且上方同类流体」，
 *       且柱按同流体切段（水 + 熔岩同列不串味）。</li>
 * </ul>
 *
 * <p><b>为什么这些断言不受随机原点影响。</b>GameTestServer 的测试网格落在随机原点
 * （见 ARMS-Core {@code docs/GameTest车道指南.md} §4），X/Z 恒在 ±1.5e7。但流体索引的坐标
 * 语义是「section 局部 y + 液面量化」，断言比较的是 {@code helper.absolutePos(...)} 得到的世界 Y
 * 与 {@code surfaceY} 的差值（0.875 / 1.0 这种整数级量），与 X/Z 无关，也不经过 float 量化——
 * 因此这里量的是索引编码本身，而不是物理引擎的浮点精度。
 *
 * <p><b>场地。</b>{@code empty_platform}（5×3×5 石砖地板），由
 * {@code tools/generate_gametest_structure.py} 生成，提交在
 * {@code src/main/resources/data/spark_core/structure/empty_platform.nbt}。框架在
 * {@code GameTestRunner#createStructuresForBatch} 读模板的时点早于 {@code @BeforeBatch}，
 * 所以模板必须先在资源里。
 *
 * <p><b>为什么要在用例里显式预约地形。</b>GameTest 世界里没有载具刚体，{@code requestStep}
 * 的 {@code updateBuild(buildBoxes)} 拿到的是空列表，不会自动构建测试区块的物理表示。
 * 用例因此走公开的 {@code SparkLevel.scheduleChunkLoad}（主线程，内部投递 PPhase.ALL），
 * 由 {@code processPendingBuildRequests} 在物理线程 IDLE 时构建 PhysicsChunk——流体索引在
 * {@code PhysicsChunk.load()} 的 {@code prepareSnapshot()} 里就发布了，不需要 section 被激活。
 *
 * <p><b>批次。</b>自成一档（{@code sparkCoreFluid}），且本类不注册 {@code @BeforeBatch}：
 * 每批至多一个批次函数，独立批次避免与同开发环境里其它模组的 {@code defaultBatch} 相撞。
 *
 * @author Sweetzonzi
 */
@GameTestHolder("spark_core")
@PrefixGameTestTemplate(false)
public class SparkFluidGameTest {

    private static final String TEMPLATE = "empty_platform";
    private static final String BATCH = "sparkCoreFluid";
    private static final float EPS = 1.0e-4f;

    /** 每个用例的流体柱都放在同一格位坐标：5×5 场地中心。外壳占 1..3，不会越出模板。 */
    private static final int CX = 2;
    private static final int CZ = 2;

    /** 自由液面在 voxel 底 + 8/9，按 1/16 量化后为 14/16 = 0.875（决策 D5 / A 案）。 */
    private static final float FREE_SURFACE = 14f / 16f;

    // =====================================================================
    // 用例
    // =====================================================================

    /** §9 #2：顶层静水（上方空气）的液面是 voxel 底 + 0.875，类型是水。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void topStaticWaterSurfaceIsEightNinths(GameTestHelper helper) {
        sealedColumn(helper, CX, CZ, 1, 1, Blocks.WATER.defaultBlockState());
        BlockPos relWater = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relWater, manager -> {
            BlockPos worldWater = helper.absolutePos(relWater);
            double x = worldWater.getX() + 0.5;
            double z = worldWater.getZ() + 0.5;
            float bottom = worldWater.getY();

            FluidSample sample = manager.sampleFluid(x, bottom + 0.5, z);
            helper.assertTrue(sample != null, "顶层静水应被流体索引命中");
            helper.assertTrue(sample.getFluidId() == waterId(),
                    "应返回水，实际 fluidId=" + sample.getFluidId());
            assertNear(helper, bottom + FREE_SURFACE, sample.getSurfaceY(),
                    "顶层静水液面应为 voxel 底 + 0.875（topQ == 14）");
            assertNear(helper, 0.375f, sample.getDepth(), "浸没深度应为液面 - 采样 Y");
        });
    }

    /** §9 #9 / P1-2：柱顶按 1/16 量化截断，柱底是最低含水 voxel 的底面，越界不 clamp。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void columnBottomAndSurfaceBoundaries(GameTestHelper helper) {
        sealedColumn(helper, CX, CZ, 1, 1, Blocks.WATER.defaultBlockState());
        BlockPos relWater = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relWater, manager -> {
            BlockPos worldWater = helper.absolutePos(relWater);
            double x = worldWater.getX() + 0.5;
            double z = worldWater.getZ() + 0.5;
            float bottom = worldWater.getY();

            helper.assertTrue(manager.isSubmerged(x, bottom + 0.5, z), "水柱内部应判为浸没");
            helper.assertTrue(manager.sampleFluid(x, bottom + 0.874, z) != null,
                    "液面之下 1 mm 应命中");
            helper.assertTrue(manager.sampleFluid(x, bottom + 0.875, z) == null,
                    "恰好落在液面上应无流体（判据是 wy >= surfaceY）");
            helper.assertTrue(manager.sampleFluid(x, bottom - 0.001, z) == null,
                    "离开最低含水 voxel 底面后应立刻无流体，且不得被 clamp 到柱内");
        });
    }

    /** §9 #5 / D2：含水台阶同时命中固体与流体两条通道，液面同样是 0.875。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void waterloggedSlabIndexesWaterChannel(GameTestHelper helper) {
        helper.setBlock(CX, 1, CZ,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));
        BlockPos relSlab = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relSlab, manager -> {
            BlockPos worldSlab = helper.absolutePos(relSlab);
            helper.assertTrue(!helper.getLevel().getBlockState(worldSlab)
                            .getCollisionShape(helper.getLevel(), worldSlab).isEmpty(),
                    "含水台阶应同时有碰撞形状");

            FluidSample sample = manager.sampleFluid(
                    worldSlab.getX() + 0.5, worldSlab.getY() + 0.5, worldSlab.getZ() + 0.5);
            helper.assertTrue(sample != null, "含水台阶的流体通道应被索引命中");
            helper.assertTrue(sample.getFluidId() == waterId(),
                    "含水台阶应返回水，实际 fluidId=" + sample.getFluidId());
            assertNear(helper, worldSlab.getY() + FREE_SURFACE, sample.getSurfaceY(),
                    "含水台阶液面应为 voxel 底 + 0.875");
        });
    }

    /** §9 #6 / D2：海草没有碰撞形状，但仍必须进入流体通道——通道独立性最关键的正例。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void seagrassIsFluidOnlyChannel(GameTestHelper helper) {
        helper.setBlock(CX, 1, CZ, Blocks.SEAGRASS.defaultBlockState());
        BlockPos relSeagrass = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relSeagrass, manager -> {
            BlockPos worldSeagrass = helper.absolutePos(relSeagrass);
            helper.assertTrue(helper.getLevel().getBlockState(worldSeagrass)
                            .getCollisionShape(helper.getLevel(), worldSeagrass).isEmpty(),
                    "海草不应有碰撞形状");

            FluidSample sample = manager.sampleFluid(
                    worldSeagrass.getX() + 0.5, worldSeagrass.getY() + 0.5, worldSeagrass.getZ() + 0.5);
            helper.assertTrue(sample != null,
                    "无碰撞形状的海草仍应进入流体通道（判据是 FluidState 非空，不是碰撞形状）");
            helper.assertTrue(sample.getFluidId() == waterId(),
                    "海草应返回水，实际 fluidId=" + sample.getFluidId());
            assertNear(helper, worldSeagrass.getY() + FREE_SURFACE, sample.getSurfaceY(),
                    "海草液面应为 voxel 底 + 0.875");
        });
    }

    /** §9 #10 / D6：同列熔岩 + 石头隔层 + 水，采样熔岩返回熔岩段的顶面而不是整列水面。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void waterAboveLavaKeepsSegmentsSeparate(GameTestHelper helper) {
        // 同一列：y=1 熔岩，y=2 石砖隔层，y=3 水；两侧封死避免流体扩散。
        helper.setBlock(CX, 1, CZ, Blocks.LAVA.defaultBlockState());
        helper.setBlock(CX, 2, CZ, Blocks.STONE_BRICKS);
        helper.setBlock(CX, 3, CZ, Blocks.WATER.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(CX - 1, y, CZ, Blocks.STONE_BRICKS);
            helper.setBlock(CX + 1, y, CZ, Blocks.STONE_BRICKS);
            helper.setBlock(CX, y, CZ - 1, Blocks.STONE_BRICKS);
            helper.setBlock(CX, y, CZ + 1, Blocks.STONE_BRICKS);
        }
        BlockPos relLava = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relLava, manager -> {
            BlockPos worldLava = helper.absolutePos(relLava);
            BlockPos worldWater = helper.absolutePos(new BlockPos(CX, 3, CZ));
            double x = worldLava.getX() + 0.5;
            double z = worldLava.getZ() + 0.5;

            FluidSample lava = manager.sampleFluid(x, worldLava.getY() + 0.5, z);
            helper.assertTrue(lava != null, "熔岩段应被例外表命中");
            helper.assertTrue(lava.getFluidId() == lavaId(),
                    "下层段应返回熔岩，实际 fluidId=" + lava.getFluidId());
            helper.assertTrue(lava.getSurfaceY() > worldLava.getY(),
                    "熔岩段液面应高于自身 voxel 底面");
            helper.assertTrue(lava.getSurfaceY() < worldWater.getY(),
                    "熔岩段液面必须低于上方水柱的底面，否则说明两段被当成一柱串了液面");

            FluidSample water = manager.sampleFluid(x, worldWater.getY() + 0.5, z);
            helper.assertTrue(water != null && water.getFluidId() == waterId(),
                    "上层水段应返回水");
            assertNear(helper, worldWater.getY() + FREE_SURFACE, water.getSurfaceY(),
                    "上层水柱为顶层静水，液面应为 voxel 底 + 0.875");
        });
    }

    /** §9 #3 + §3.4⑤：跨 section 柱顶 topQ == 16，且续接标记被置位（不能用 topQ 判续接）。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void crossSectionColumnReachesTop(GameTestHelper helper) {
        BlockPos relFloor = new BlockPos(CX, 0, CZ);
        BlockPos worldFloor = helper.absolutePos(relFloor);
        int cross = 16 - (worldFloor.getY() & 15);   // 下一 section 的首个相对 y
        int lowerRelY = cross - 1;                    // 本 section 的最后一个相对 y

        helper.setBlock(CX, lowerRelY - 1, CZ, Blocks.STONE_BRICKS);
        helper.setBlock(CX, lowerRelY, CZ, Blocks.WATER.defaultBlockState());
        helper.setBlock(CX, cross, CZ, Blocks.WATER.defaultBlockState());
        for (int y = lowerRelY; y <= cross; y++) {
            helper.setBlock(CX - 1, y, CZ, Blocks.STONE_BRICKS);
            helper.setBlock(CX + 1, y, CZ, Blocks.STONE_BRICKS);
            helper.setBlock(CX, y, CZ - 1, Blocks.STONE_BRICKS);
            helper.setBlock(CX, y, CZ + 1, Blocks.STONE_BRICKS);
        }
        BlockPos relLower = new BlockPos(CX, lowerRelY, CZ);

        withTerrain(helper, relLower, manager -> {
            BlockPos worldLower = helper.absolutePos(relLower);
            BlockPos worldCross = helper.absolutePos(new BlockPos(CX, cross, CZ));
            int chunkX = SectionPos.blockToSectionCoord(worldLower.getX());
            int chunkZ = SectionPos.blockToSectionCoord(worldLower.getZ());
            int sectionY = SectionPos.blockToSectionCoord(worldLower.getY());

            PhysicsChunkSection lower = manager.getSectionByCoords(chunkX, chunkZ, sectionY);
            helper.assertTrue(lower != null, "下层 section 应随区块构建而出现");
            SectionFluidIndex index = lower.getFluidIndex();
            helper.assertTrue(index != null, "下层 section 应已发布流体索引");

            int localX = worldLower.getX() & 15;
            int localZ = worldLower.getZ() & 15;
            int column = SectionFluidIndex.colIndexOf(localX, localZ);
            helper.assertTrue(index.reachesTop(column),
                    "柱顶局部 y == 15 且上方同类流体，续接标记应被置位");

            double x = worldLower.getX() + 0.5;
            double z = worldLower.getZ() + 0.5;
            FluidSample lowerSample = manager.sampleFluid(x, worldLower.getY() + 0.5, z);
            helper.assertTrue(lowerSample != null, "下层水柱应命中");
            assertNear(helper, worldCross.getY(), lowerSample.getSurfaceY(), EPS,
                    "下层柱顶 topQ == 16，液面截断到下一 section 的底面");

            FluidSample upperSample = manager.sampleFluid(
                    x, worldCross.getY() + 0.5, z);
            helper.assertTrue(upperSample != null, "上层水柱应命中");
            assertNear(helper, worldCross.getY() + FREE_SURFACE, upperSample.getSurfaceY(), EPS,
                    "上层 section 的自由液面仍为 voxel 底 + 0.875");
        });
    }

    /** P0-1 回归 / §9 #8：纯流体 section 在**从未激活**时也必须已发布流体索引。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void pureFluidSectionPublishesIndexWithoutActivation(GameTestHelper helper) {
        BlockPos relFloor = new BlockPos(CX, 0, CZ);
        BlockPos worldFloor = helper.absolutePos(relFloor);
        int upperRelY = 16 - (worldFloor.getY() & 15);   // 地板上方第一个 section 内的相对 y

        // 刻意不封壳、不放任何固体：该 section 应当只有流体通道。
        helper.setBlock(CX, upperRelY, CZ, Blocks.WATER.defaultBlockState());
        BlockPos relUpper = new BlockPos(CX, upperRelY, CZ);

        // 只预约地板所在的 section；上层 section 保持在未激活状态。
        withTerrain(helper, relFloor, manager -> {
            BlockPos worldUpper = helper.absolutePos(relUpper);
            int chunkX = SectionPos.blockToSectionCoord(worldUpper.getX());
            int chunkZ = SectionPos.blockToSectionCoord(worldUpper.getZ());
            int sectionY = SectionPos.blockToSectionCoord(worldUpper.getY());

            PhysicsChunkSection upper = manager.getSectionByCoords(chunkX, chunkZ, sectionY);
            helper.assertTrue(upper != null, "上层 section 应随区块构建而出现");
            helper.assertTrue(!upper.isActive(),
                    "纯流体 section 不应被激活，也不会建出刚体");
            helper.assertTrue(upper.getFluidIndex() != null,
                    "未激活的纯流体 section 也必须已发布流体索引（P0-1 回归）");

            FluidSample sample = manager.sampleFluid(
                    worldUpper.getX() + 0.5, worldUpper.getY() + 0.5, worldUpper.getZ() + 0.5);
            helper.assertTrue(sample != null, "未激活的纯流体 section 也应能采样到水");
            helper.assertTrue(sample.getFluidId() == waterId(),
                    "应返回水，实际 fluidId=" + sample.getFluidId());
        });
    }

    /** §9 #7：只有固体的 section 不产生流体索引；未被放置流体的列不得被标记。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void plainStoneColumnHasNoFluid(GameTestHelper helper) {
        // 不放置任何流体，只用模板自带的地板。
        BlockPos relStone = new BlockPos(CX, 0, CZ);
        withTerrain(helper, relStone, manager -> {
            BlockPos worldStone = helper.absolutePos(relStone);
            int chunkX = SectionPos.blockToSectionCoord(worldStone.getX());
            int chunkZ = SectionPos.blockToSectionCoord(worldStone.getZ());
            int sectionY = SectionPos.blockToSectionCoord(worldStone.getY());

            PhysicsChunkSection section = manager.getSectionByCoords(chunkX, chunkZ, sectionY);
            helper.assertTrue(section != null, "地板 section 应随区块构建而出现");

            // 同 chunk 的相邻格位可能有别的用例放的水，因此这里断言「本列无流体」而不是「整节索引为 null」。
            int localX = worldStone.getX() & 15;
            int localZ = worldStone.getZ() & 15;
            SectionFluidIndex index = section.getFluidIndex();
            if (index != null) {
                helper.assertFalse(index.hasColumn(SectionFluidIndex.colIndexOf(localX, localZ)),
                        "从未放置流体的列不应被流体索引标记");
            }
            helper.assertTrue(manager.sampleFluid(
                            worldStone.getX() + 0.5, worldStone.getY() + 0.5, worldStone.getZ() + 0.5) == null,
                    "石头内部不应采样到流体");
        });
    }

    /** P0-3 / D8：批量路径与单点路径一致，且严格局部换算不会把邻格水位 clamp 过来。 */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void batchSamplingMatchesSingleAndStaysStrict(GameTestHelper helper) {
        sealedColumn(helper, CX, CZ, 1, 1, Blocks.WATER.defaultBlockState());
        BlockPos relWater = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relWater, manager -> {
            BlockPos worldWater = helper.absolutePos(relWater);
            double x = worldWater.getX() + 0.5;
            double z = worldWater.getZ() + 0.5;
            double bottom = worldWater.getY();

            double[] points = {
                    x, bottom + 0.5, z,        // 命中
                    x, bottom + 0.99, z,       // 液面之上
                    x, bottom - 0.5, z,        // 柱底之外（石头）
                    x, bottom - 64.0, z,       // 远在下方、且被 section 定位拒绝
                    x, bottom + 4.0, z         // 上层空气
            };
            int n = points.length / 3;
            int[] ids = new int[n];
            float[] surfaces = new float[n];
            int hits = manager.sampleFluidBatch(points, ids, surfaces);

            helper.assertTrue(hits == 1, "批量采样应只命中水柱内部那一点，实际命中 " + hits);
            for (int i = 0; i < n; i++) {
                FluidSample single = manager.sampleFluid(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]);
                if (ids[i] >= 0) {
                    helper.assertTrue(single != null, "批量命中而单点未命中：点 " + i);
                    helper.assertTrue(single.getFluidId() == ids[i], "点 " + i + " 的类型不一致");
                    assertNear(helper, single.getSurfaceY(), surfaces[i], EPS,
                            "点 " + i + " 的液面不一致");
                } else {
                    helper.assertTrue(single == null, "批量未命中而单点命中：点 " + i);
                    helper.assertTrue(ids[i] == -1, "点 " + i + " 未命中时输出应为 -1");
                }
            }
        });
    }

    /**
     * 深水柱跨**两个** section 边界：验证每个被完全淹没的 section 都把液面"截断"到自己的顶面。
     *
     * <p>场景（相对 y）：S0 是地板所在 section，S1、S2 依次向上；水从 y=1 连续灌到 y=topRel，
     * 真实自由液面在 S2 内部。逐段期望：
     * <ul>
     *   <li>S0 柱顶局部 y==15，上方同类流体 → topQ==16，采样返回的液面 = S1 的底面（截断）；</li>
     *   <li>S1 同理，采样返回的液面 = S2 的底面（截断），误差再叠一层；</li>
     *   <li>S2 柱顶局部 y==3（空气中），topQ==14，采样返回真实液面 +0.875；</li>
     *   <li>S0、S1 的续接标记置位，S2 不置位。</li>
     * </ul>
     * 这条用例把计划 §10.1「Phase 1 先按截断处理」的行为钉成可执行的规格：下游若不做跨 section
     * 续接，深处读到的 depth 会随"层"偏小，而不是偏大。
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 300)
    public static void multiSectionColumnTruncatesAtEachSectionTop(GameTestHelper helper) {
        BlockPos relFloor = new BlockPos(CX, 0, CZ);
        BlockPos worldFloor = helper.absolutePos(relFloor);
        int s1Rel = 16 - (worldFloor.getY() & 15);   // 第 1 个后继 section 的首格
        int s2Rel = s1Rel + 16;                       // 第 2 个后继 section 的首格
        int topRel = s2Rel + 3;                       // 真实液面所在格（在 S2 内部）

        for (int y = 1; y <= topRel; y++) {
            helper.setBlock(CX, y, CZ, Blocks.WATER.defaultBlockState());
            helper.setBlock(CX - 1, y, CZ, Blocks.STONE_BRICKS);
            helper.setBlock(CX + 1, y, CZ, Blocks.STONE_BRICKS);
            helper.setBlock(CX, y, CZ - 1, Blocks.STONE_BRICKS);
            helper.setBlock(CX, y, CZ + 1, Blocks.STONE_BRICKS);
        }
        BlockPos relBottom = new BlockPos(CX, 1, CZ);

        withTerrain(helper, relBottom, manager -> {
            float base = helper.absolutePos(relFloor).getY();
            double x = helper.absolutePos(relBottom).getX() + 0.5;
            double z = helper.absolutePos(relBottom).getZ() + 0.5;

            PhysicsChunkSection s0 = sectionFor(helper, manager, 1);
            PhysicsChunkSection s1 = sectionFor(helper, manager, s1Rel);
            PhysicsChunkSection s2 = sectionFor(helper, manager, topRel);
            helper.assertTrue(s0 != null && s1 != null && s2 != null, "三个 section 都应已构建");

            int localX = helper.absolutePos(relBottom).getX() & 15;
            int localZ = helper.absolutePos(relBottom).getZ() & 15;
            int column = SectionFluidIndex.colIndexOf(localX, localZ);
            helper.assertTrue(s0.getFluidIndex() != null && s1.getFluidIndex() != null,
                    "被淹没的下层 section 也应发布流体索引");
            helper.assertTrue(s0.getFluidIndex().reachesTop(column), "S0 柱顶续接标记应置位");
            helper.assertTrue(s1.getFluidIndex().reachesTop(column), "S1 柱顶续接标记应置位");
            helper.assertTrue(s2.getFluidIndex() == null || !s2.getFluidIndex().reachesTop(column),
                    "S2 柱顶在空气中，不应置续接标记");

            FluidSample deep = manager.sampleFluid(x, base + 1.5, z);
            helper.assertTrue(deep != null && deep.getFluidId() == waterId(), "S0 深处应采样到水");
            assertNear(helper, base + s1Rel, deep.getSurfaceY(), EPS,
                    "S0 被截断的液面应为 S1 的底面");
            float trueSurface = base + topRel + FREE_SURFACE;
            helper.assertFalse(deep.getSurfaceY() >= trueSurface,
                    "截断液面必须不高于真实液面（否则下游会高估浸没）");

            FluidSample mid = manager.sampleFluid(x, base + s1Rel + 15.5, z);
            helper.assertTrue(mid != null && mid.getFluidId() == waterId(), "S1 深处应采样到水");
            assertNear(helper, base + s2Rel, mid.getSurfaceY(), EPS,
                    "S1 被截断的液面应为 S2 的底面");

            FluidSample top = manager.sampleFluid(x, base + topRel + 0.5, z);
            helper.assertTrue(top != null && top.getFluidId() == waterId(), "S2 顶层水应采样到水");
            assertNear(helper, trueSurface, top.getSurfaceY(), EPS,
                    "含真实液面的 section 返回 voxel 底 + 0.875");
        });
    }

    // =====================================================================
    // 基础设施
    // =====================================================================

    /** 一个用例的断言体；由 {@link #withTerrain} 在每 tick 重试，直到不再抛异常。 */
    @FunctionalInterface
    private interface TerrainAssertion {
        void run(PhysicsChunkManager manager);
    }

    /**
     * 预约 {@code relSchedulePos} 所在区块/ section 的物理地形，然后**每 tick 重试** [assertion]
     * 直到它不再抛异常。
     *
     * <p>{@code GameTestHelper#succeedWhen} 的语义正是「反复执行直到成功，超时后以最后一次异常失败」，
     * 用它同时吞掉两段异步：物理线程/ 物理地形管理器的初始化，以及
     * {@code scheduleChunkLoad → processPendingBuildRequests → PhysicsChunk.load()} 的构建延迟。
     * 断言在服务端主线程执行；流体索引是不可变实例、由 volatile 发布，主线程读它是安全的。
     */
    private static void withTerrain(GameTestHelper helper, BlockPos relSchedulePos, TerrainAssertion assertion) {
        ServerLevel level = helper.getLevel();
        BlockPos worldSchedulePos = helper.absolutePos(relSchedulePos);
        ChunkPos chunkPos = new ChunkPos(worldSchedulePos);
        boolean[] scheduled = {false};

        helper.succeedWhen(() -> {
            PhysicsChunkManager manager = SparkLevel.getPhysicsLevel(level).getTerrainManager();
            if (!scheduled[0]) {
                SparkLevel.scheduleChunkLoad(level, chunkPos,
                        worldSchedulePos.getY(), worldSchedulePos.getY(), 0, 200);
                scheduled[0] = true;
            }
            assertion.run(manager);
        });
    }

    private static PhysicsChunkSection sectionFor(GameTestHelper helper, PhysicsChunkManager manager, int relY) {
        BlockPos world = helper.absolutePos(new BlockPos(CX, relY, CZ));
        return manager.getSectionByCoords(
                SectionPos.blockToSectionCoord(world.getX()),
                SectionPos.blockToSectionCoord(world.getZ()),
                SectionPos.blockToSectionCoord(world.getY()));
    }

    /**
     * 在 (x, z) 造一根被石砖四面 + 底面封死的流体柱，避免流水/ 岩浆在断言期间扩散。
     * 顶面留空——流体不会向上流。
     */
    private static void sealedColumn(GameTestHelper helper, int x, int z, int bottomRelY, int height, BlockState fluid) {
        helper.setBlock(x, bottomRelY - 1, z, Blocks.STONE_BRICKS);
        for (int y = bottomRelY; y < bottomRelY + height; y++) {
            helper.setBlock(x, y, z, fluid);
            helper.setBlock(x - 1, y, z, Blocks.STONE_BRICKS);
            helper.setBlock(x + 1, y, z, Blocks.STONE_BRICKS);
            helper.setBlock(x, y, z - 1, Blocks.STONE_BRICKS);
            helper.setBlock(x, y, z + 1, Blocks.STONE_BRICKS);
        }
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String message) {
        assertNear(helper, expected, actual, EPS, message);
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, float eps, String message) {
        helper.assertTrue(Math.abs(expected - actual) <= eps,
                message + "：预期 " + expected + "，实际 " + actual);
    }

    private static int waterId() {
        return BuiltInRegistries.FLUID.getId(Fluids.WATER);
    }

    private static int lavaId() {
        return BuiltInRegistries.FLUID.getId(Fluids.LAVA);
    }
}