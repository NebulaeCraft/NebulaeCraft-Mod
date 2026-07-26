package net.kuina.nebulaecraft.autogen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.kuina.nebulaecraft.block.BlockAutogenMarker;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TunnelBuilder {
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private TunnelBuilder() {
    }

    public static TunnelPlan build(WorldServer world, AutogenSelection.Selection selection, String presetId,
                                   String platformColor, String power, boolean mirrored) throws TunnelBuildException {
        if (selection == null || !selection.isComplete()) {
            throw new TunnelBuildException("请先用自动生成魔杖选择两个标记方块");
        }
        if (selection.start.dimension != selection.end.dimension || selection.start.dimension != world.provider.getDimension()) {
            throw new TunnelBuildException("两个标记必须与玩家位于同一维度");
        }
        if (!isMarker(world, selection.start.pos) || !isMarker(world, selection.end.pos)) {
            throw new TunnelBuildException("已选择的标记方块不存在或已被移动");
        }
        if (world.getBlockState(selection.start.pos).getValue(BlockAutogenMarker.FACING) != selection.start.facing
                || world.getBlockState(selection.end.pos).getValue(BlockAutogenMarker.FACING) != selection.end.facing) {
            throw new TunnelBuildException("标记在选择后被旋转，请用魔杖重新选择两个标记");
        }
        TunnelConfig.Preset preset = TunnelConfig.getPreset(presetId);
        if (preset == null) {
            throw new TunnelBuildException("未知隧道预设: " + presetId);
        }
        int colorMeta = colorMeta(platformColor);
        String normalizedPower = power.toLowerCase(Locale.ENGLISH);
        if (!normalizedPower.equals("none") && !normalizedPower.equals("catenary")
                && !normalizedPower.equals("thirdrail_white") && !normalizedPower.equals("thirdrail_yellow")) {
            throw new TunnelBuildException("未知供电方式: " + power);
        }

        Geometry geometry = createGeometry(selection, preset);
        TunnelConfig.Settings settings = TunnelConfig.get().settings;
        if (geometry.length > settings.maxPathLength) {
            throw new TunnelBuildException("路线长度超过配置上限 " + settings.maxPathLength);
        }

        IBlockState concrete = resolveState(preset.fullConcrete);
        IBlockState horizontalSlab = resolveState(preset.horizontalConcreteSlab);
        IBlockState verticalSlab = resolveState(preset.verticalConcreteSlab);
        IBlockState centerBed = resolveState(preset.centerTrackbed);
        IBlockState sideBed = resolveState(preset.sideTrackbed);
        Block reinforcedTrack = resolveBlock(preset.reinforcedTrack);
        Block platform = resolveBlock(preset.platform);
        IBlockState platformState = platform.getStateFromMeta(colorMeta);

        LinkedHashMap<Long, PlannedState> states = new LinkedHashMap<>();
        List<BlockPos> route = geometry.route;
        List<EnumFacing> sectionFacings = sectionFacings(route);
        Set<Integer> catenarySupportIndices = normalizedPower.equals("catenary")
                ? catenarySupportIndices(route, settings.catenarySupportSpacing)
                : Collections.<Integer>emptySet();
        ThirdRailLayout thirdRailLayout = normalizedPower.startsWith("thirdrail_")
                ? createThirdRailLayout(route, sectionFacings, mirrored,
                settings.thirdRailSupportSpacing)
                : null;
        int halfClearWidth = preset.clearWidth / 2;
        int shellLateral = halfClearWidth + 1;
        int catenaryY = preset.clearHeight + 1;
        int roofY = catenaryY + preset.catenaryRecessHeight;
        for (int i = 0; i < route.size(); i++) {
            BlockPos center = route.get(i);
            EnumFacing trackForward = routeFacing(route, i);
            EnumFacing forward = sectionFacings.get(i);
            boolean slopeSection = isSlopeRailCell(route, i);
            int nx = -forward.getFrontOffsetZ();
            int nz = forward.getFrontOffsetX();
            if (mirrored) {
                nx = -nx;
                nz = -nz;
            }

            // Exact seven-by-seven reference section, from bottom to top:
            // CC 44 43 44 CC / C AA R AA C / V AAAAA V / V AAAAA V /
            // C AAAAA C / CC S K S CC / CCCCCCC.
            // Lining is planned before clearance. Clearance has the higher priority so the union
            // of neighbouring slices remains open through curves instead of leaving transverse piers.
            int sectionRoofY = roofY + (slopeSection ? 1 : 0);
            for (int lateral = -shellLateral; lateral <= shellLateral; lateral++) {
                put(states, offset(center, nx, nz, lateral, 0), concrete, 10);
                put(states, offset(center, nx, nz, lateral, sectionRoofY), concrete, 10);
            }

            // A slope rail rises through the upper half of this slice. Its special eight-layer
            // section replaces the normal upper full-block wall with a third vertical-slab row.
            put(states, offset(center, nx, nz, -shellLateral, 1), concrete, 10);
            put(states, offset(center, nx, nz, shellLateral, 1), concrete, 10);
            if (!slopeSection) {
                put(states, offset(center, nx, nz, -shellLateral, preset.clearHeight), concrete, 10);
                put(states, offset(center, nx, nz, shellLateral, preset.clearHeight), concrete, 10);
            }

            // Normal slices have two middle slab rows; slope slices have three.
            EnumFacing leftOutward = lateralFacing(nx, nz, -1);
            EnumFacing rightOutward = lateralFacing(nx, nz, 1);
            IBlockState leftVerticalSlab = sideMountedState(verticalSlab.getBlock(), leftOutward);
            IBlockState rightVerticalSlab = sideMountedState(verticalSlab.getBlock(), rightOutward);
            int verticalSlabTopY = slopeSection ? preset.clearHeight : preset.clearHeight - 1;
            for (int y = 2; y <= verticalSlabTopY; y++) {
                put(states, offset(center, nx, nz, -shellLateral, y), leftVerticalSlab, 11);
                put(states, offset(center, nx, nz, shellLateral, y), rightVerticalSlab, 11);
            }

            if (slopeSection) {
                // Slope section, bottom to top around the contact wire:
                // V AAAAA V / C AA K AA C / CC S A S CC / CCCCCCC.
                put(states, offset(center, nx, nz, -shellLateral, catenaryY), concrete, 10);
                put(states, offset(center, nx, nz, shellLateral, catenaryY), concrete, 10);
                put(states, offset(center, nx, nz, -shellLateral, roofY), concrete, 10);
                put(states, offset(center, nx, nz, -halfClearWidth, roofY), concrete, 10);
                put(states, offset(center, nx, nz, -1, roofY), horizontalSlab, 11);
                put(states, offset(center, nx, nz, 1, roofY), horizontalSlab, 11);
                put(states, offset(center, nx, nz, halfClearWidth, roofY), concrete, 10);
                put(states, offset(center, nx, nz, shellLateral, roofY), concrete, 10);
            } else {
                // Normal catenary row: two blocks, upper slab, wire, upper slab, two blocks.
                put(states, offset(center, nx, nz, -shellLateral, catenaryY), concrete, 10);
                put(states, offset(center, nx, nz, -halfClearWidth, catenaryY), concrete, 10);
                put(states, offset(center, nx, nz, -1, catenaryY), horizontalSlab, 11);
                put(states, offset(center, nx, nz, 1, catenaryY), horizontalSlab, 11);
                put(states, offset(center, nx, nz, halfClearWidth, catenaryY), concrete, 10);
                put(states, offset(center, nx, nz, shellLateral, catenaryY), concrete, 10);
            }

            for (int lateral = -halfClearWidth; lateral <= halfClearWidth; lateral++) {
                int clearTopY = slopeSection ? catenaryY : preset.clearHeight;
                for (int y = 1; y <= clearTopY; y++) {
                    put(states, offset(center, nx, nz, lateral, y), Blocks.AIR.getDefaultState(), 20);
                }
            }
            put(states, offset(center, nx, nz, 0, slopeSection ? roofY : catenaryY),
                    Blocks.AIR.getDefaultState(), 20);

            // Normal trackbed is 44:0, 43:8, 44:0. The slope rail needs a solid three-block
            // 43:8 base so the rising Railcraft model is supported across the whole section.
            put(states, center, centerBed, 30);
            put(states, offset(center, nx, nz, -1, 0), slopeSection ? centerBed : sideBed, 30);
            put(states, offset(center, nx, nz, 1, 0), slopeSection ? centerBed : sideBed, 30);
            put(states, center.up(), reinforcedTrack.getStateFromMeta(railMeta(route, i)), 50);

            // The optional colored maintenance platform sits on the intact left floor block.
            put(states, offset(center, nx, nz, -2, 1), platformState, 40);

            if (normalizedPower.startsWith("thirdrail_")) {
                boolean yellow = normalizedPower.endsWith("yellow");
                EnumFacing thirdRailTowardTrack = lateralFacing(nx, nz, -1);
                IBlockState thirdRail = thirdRailState(yellow, route, i, trackForward,
                        thirdRailTowardTrack, thirdRailLayout.diagonalTypes[i],
                        thirdRailLayout.diagonalFacings[i], thirdRailLayout.supports[i]);
                BlockPos thirdRailPos = offset(center, nx, nz, 1, 1);
                if (thirdRailLayout.placementOffsets[i] != null) {
                    thirdRailPos = thirdRailPos.add(thirdRailLayout.placementOffsets[i]);
                }
                put(states, thirdRailPos, thirdRail, 45);
            } else if (normalizedPower.equals("catenary")) {
                IBlockState catenary = catenaryState(route, i, trackForward,
                        catenarySupportIndices.contains(i));
                put(states, center.up(catenaryY), catenary, 45);
            }

            if (i % settings.lightSpacing == 0) {
                // Keep every light on the metal-platform side. Mirroring reverses nx/nz above,
                // so the platform and its lights move to the opposite physical side together.
                int lateral = -halfClearWidth;
                EnumFacing attachmentSide = lateralFacing(nx, nz, -1);
                Block light = resolveBlock("nebulaecraft:tunnel_light@0");
                IBlockState lightState = sideMountedState(light, attachmentSide);
                BlockPos lightPos = offset(center, nx, nz, lateral, 4);
                if (slopeSection) {
                    lightPos = lightPos.up();
                }
                if (turnLightNeedsOutset(route, sectionFacings, i, attachmentSide, mirrored)) {
                    lightPos = lightPos.offset(attachmentSide);
                }
                put(states, lightPos, lightState, 60);
            }
        }

        applyTurnSectionTransitions(states, route, sectionFacings, concrete, horizontalSlab,
                verticalSlab, sideBed, centerBed, platformState, preset.clearHeight,
                preset.catenaryRecessHeight, mirrored);

        if (states.size() > settings.maxChangedBlocks) {
            throw new TunnelBuildException("预计修改方块数超过配置上限 " + settings.maxChangedBlocks);
        }
        List<PlannedState> orderedStates = new ArrayList<>(states.values());
        Collections.sort(orderedStates, Comparator.comparingInt(value -> value.priority));
        List<TunnelPlan.Operation> operations = new ArrayList<>(orderedStates.size());
        for (PlannedState planned : orderedStates) {
            operations.add(new TunnelPlan.Operation(planned.pos, planned.state));
        }
        return new TunnelPlan(world.provider.getDimension(), operations, new ArrayList<>(route), geometry.length,
                geometry.minimumRadius, geometry.maximumGrade, presetId, normalizeColor(platformColor),
                normalizedPower, mirrored);
    }

    /**
     * A one-block lateral move is rasterized as two adjacent rail corners. Normal seven-wide
     * sections overwrite each other and produce a pinched, stepped tunnel. Widen all seven layers
     * across the three affected rows. The floor follows the explicit eight-wide template:
     *
     *   CC S D S S CC
     *   CC S D D S CC
     *   CC S S D S CC
     *
     * D is 43:8, S is 44:0. The template is rotated from the actual lateral step, so it works for
     * all four directions and both left/right shifts.
     */
    private static void applyTurnSectionTransitions(Map<Long, PlannedState> states, List<BlockPos> route,
                                                     List<EnumFacing> sectionFacings, IBlockState concrete,
                                                     IBlockState horizontalSlab, IBlockState verticalSlab,
                                                     IBlockState sideBed, IBlockState centerBed,
                                                     IBlockState platformState, int clearHeight,
                                                     int recessHeight, boolean mirrored)
            throws TunnelBuildException {
        for (int i = 1; i + 2 < route.size(); i++) {
            BlockPos before = route.get(i - 1);
            BlockPos oldCorner = route.get(i);
            BlockPos newCorner = route.get(i + 1);
            BlockPos after = route.get(i + 2);
            if (before.getY() != oldCorner.getY() || oldCorner.getY() != newCorner.getY()
                    || newCorner.getY() != after.getY()) {
                continue;
            }

            EnumFacing incoming = horizontalDirection(before, oldCorner);
            EnumFacing shift = horizontalDirection(oldCorner, newCorner);
            EnumFacing outgoing = horizontalDirection(newCorner, after);
            if (incoming == null || shift == null || outgoing != incoming
                    || shift.getAxis() == incoming.getAxis()
                    || sectionFacings.get(i).getAxis() != incoming.getAxis()) {
                continue;
            }

            putTurnSectionRow(states, before, shift, concrete, horizontalSlab, verticalSlab,
                    sideBed, centerBed, clearHeight, recessHeight,
                    new int[]{-1, 1, 2}, new int[]{0});
            putTurnSectionRow(states, oldCorner, shift, concrete, horizontalSlab, verticalSlab,
                    sideBed, centerBed, clearHeight, recessHeight,
                    new int[]{-1, 2}, new int[]{0, 1});
            BlockPos projectedOldCenter = after.offset(shift.getOpposite());
            putTurnSectionRow(states, projectedOldCenter, shift, concrete, horizontalSlab, verticalSlab,
                    sideBed, centerBed, clearHeight, recessHeight,
                    new int[]{-1, 0, 2}, new int[]{1});

            addTurnOuterCornerColumns(states, before, projectedOldCenter, incoming, shift,
                    concrete, clearHeight + 1 + recessHeight);
            adjustTurnPlatform(states, before, oldCorner, newCorner, after, incoming, shift,
                    platformState, mirrored);
        }
    }

    /**
     * The widened three-row transition meets the narrower straight sections at two diagonal
     * outside corners. A side slab at either seam exposes the world beyond its missing half.
     * Fill the actual seam corners, not another block laterally outside the transition, with
     * complete floor-to-roof concrete columns.
     */
    private static void addTurnOuterCornerColumns(Map<Long, PlannedState> states,
                                                  BlockPos firstTransitionCenter,
                                                  BlockPos lastTransitionCenter,
                                                  EnumFacing forward, EnumFacing shift,
                                                  IBlockState concrete, int roofY) {
        BlockPos incomingCorner = firstTransitionCenter.offset(shift, 4)
                .offset(forward.getOpposite());
        BlockPos outgoingCorner = lastTransitionCenter.offset(shift, -3)
                .offset(forward);
        for (int y = 0; y <= roofY; y++) {
            put(states, incomingCorner.up(y), concrete, 35);
            put(states, outgoingCorner.up(y), concrete, 35);
        }
    }

    /**
     * Keep the two straight platform runs diagonally separated at a one-block lateral move.
     * Connecting the platform at both rail-corner cells produces a two-block-wide bridge across
     * the transition. On the side toward the move, trim the last two incoming cells and start the
     * outgoing run one cell early. On the opposite side, apply the longitudinal mirror image.
     */
    private static void adjustTurnPlatform(Map<Long, PlannedState> states, BlockPos before,
                                           BlockPos oldCorner, BlockPos newCorner, BlockPos after,
                                           EnumFacing incoming, EnumFacing shift,
                                           IBlockState platformState, boolean mirrored) {
        EnumFacing platformSide = mirrored ? incoming.rotateY() : incoming.rotateYCCW();
        if (shift == platformSide) {
            put(states, platformPosition(before, incoming, mirrored), Blocks.AIR.getDefaultState(), 70);
            put(states, platformPosition(oldCorner, incoming, mirrored), Blocks.AIR.getDefaultState(), 70);
            put(states, platformPosition(before.offset(shift), incoming, mirrored), platformState, 71);
        } else {
            put(states, platformPosition(newCorner, incoming, mirrored), Blocks.AIR.getDefaultState(), 70);
            put(states, platformPosition(after, incoming, mirrored), Blocks.AIR.getDefaultState(), 70);
            put(states, platformPosition(after.offset(shift.getOpposite()), incoming, mirrored),
                    platformState, 71);
        }
    }

    private static BlockPos platformPosition(BlockPos center, EnumFacing forward, boolean mirrored) {
        EnumFacing platformSide = mirrored ? forward.rotateY() : forward.rotateYCCW();
        return center.offset(platformSide, 2).up();
    }

    /**
     * A light on the old-center half of an outward transition (or the new-center half of the
     * mirrored transition) is one block inside the widened wall. Move only those lights outward;
     * lights on the other half already coincide with the transition wall.
     */
    private static boolean turnLightNeedsOutset(List<BlockPos> route, List<EnumFacing> sectionFacings,
                                                int lightIndex, EnumFacing platformSide,
                                                boolean mirrored) {
        int first = Math.max(1, lightIndex - 2);
        int last = Math.min(route.size() - 3, lightIndex + 1);
        for (int i = first; i <= last; i++) {
            BlockPos before = route.get(i - 1);
            BlockPos oldCorner = route.get(i);
            BlockPos newCorner = route.get(i + 1);
            BlockPos after = route.get(i + 2);
            if (before.getY() != oldCorner.getY() || oldCorner.getY() != newCorner.getY()
                    || newCorner.getY() != after.getY()) {
                continue;
            }
            EnumFacing incoming = horizontalDirection(before, oldCorner);
            EnumFacing shift = horizontalDirection(oldCorner, newCorner);
            EnumFacing outgoing = horizontalDirection(newCorner, after);
            if (incoming == null || shift == null || outgoing != incoming
                    || shift.getAxis() == incoming.getAxis()
                    || sectionFacings.get(i).getAxis() != incoming.getAxis()) {
                continue;
            }
            EnumFacing transitionPlatformSide = mirrored ? incoming.rotateY() : incoming.rotateYCCW();
            if (platformSide != transitionPlatformSide) {
                continue;
            }
            if (shift == transitionPlatformSide) {
                return lightIndex == i - 1 || lightIndex == i;
            }
            return lightIndex == i + 1 || lightIndex == i + 2;
        }
        return false;
    }

    private static void putTurnSectionRow(Map<Long, PlannedState> states, BlockPos oldCenter,
                                          EnumFacing shift, IBlockState concrete,
                                          IBlockState horizontalSlab, IBlockState verticalSlab,
                                          IBlockState sideBed, IBlockState centerBed,
                                          int clearHeight, int recessHeight,
                                          int[] sideOffsets, int[] centerOffsets)
            throws TunnelBuildException {
        int catenaryY = clearHeight + 1;
        int roofY = catenaryY + recessHeight;

        // Eight-wide floor and roof.
        for (int offset = -3; offset <= 4; offset++) {
            put(states, oldCenter.offset(shift, offset), concrete, 35);
            put(states, oldCenter.offset(shift, offset).up(roofY), concrete, 35);
        }

        // Six-wide clear opening. Higher-priority air removes the two old inner walls left where
        // the seven-wide sections overlap.
        for (int offset = -2; offset <= 3; offset++) {
            for (int y = 1; y <= clearHeight; y++) {
                put(states, oldCenter.offset(shift, offset).up(y), Blocks.AIR.getDefaultState(), 37);
            }
        }
        put(states, oldCenter.offset(shift, -3).up(1), concrete, 35);
        put(states, oldCenter.offset(shift, 4).up(1), concrete, 35);
        put(states, oldCenter.offset(shift, -3).up(clearHeight), concrete, 35);
        put(states, oldCenter.offset(shift, 4).up(clearHeight), concrete, 35);

        IBlockState lowVerticalSlab = sideMountedState(verticalSlab.getBlock(), shift.getOpposite());
        IBlockState highVerticalSlab = sideMountedState(verticalSlab.getBlock(), shift);
        for (int y = 2; y <= 3; y++) {
            put(states, oldCenter.offset(shift, -3).up(y), lowVerticalSlab, 36);
            put(states, oldCenter.offset(shift, 4).up(y), highVerticalSlab, 36);
        }

        // The catenary recess follows the same 1 -> 2 -> 1 lateral transition as the trackbed.
        for (int offset = -3; offset <= 4; offset++) {
            put(states, oldCenter.offset(shift, offset).up(catenaryY), concrete, 35);
        }
        for (int offset : sideOffsets) {
            put(states, oldCenter.offset(shift, offset), sideBed, 36);
            put(states, oldCenter.offset(shift, offset).up(catenaryY), horizontalSlab, 36);
        }
        for (int offset : centerOffsets) {
            put(states, oldCenter.offset(shift, offset), centerBed, 37);
            put(states, oldCenter.offset(shift, offset).up(catenaryY), Blocks.AIR.getDefaultState(), 37);
        }
    }

    public static List<String> colorNames() {
        List<String> result = new ArrayList<>();
        for (String color : COLORS) {
            result.add(color);
        }
        return result;
    }

    private static Geometry createGeometry(AutogenSelection.Selection selection, TunnelConfig.Preset preset)
            throws TunnelBuildException {
        AutogenSelection.Anchor start = selection.start;
        AutogenSelection.Anchor end = selection.end;
        double dx = end.pos.getX() - start.pos.getX();
        double dz = end.pos.getZ() - start.pos.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 3.0) {
            throw new TunnelBuildException("两个标记的水平距离至少需要 3 格");
        }
        double startDot = start.facing.getFrontOffsetX() * dx + start.facing.getFrontOffsetZ() * dz;
        double endDot = end.facing.getFrontOffsetX() * -dx + end.facing.getFrontOffsetZ() * -dz;
        if (startDot <= 0 || endDot <= 0) {
            throw new TunnelBuildException("两个标记的箭头必须朝向彼此所在的一侧");
        }

        Vec p0 = new Vec(start.pos.getX(), start.pos.getY(), start.pos.getZ());
        Vec p1 = new Vec(end.pos.getX(), end.pos.getY(), end.pos.getZ());
        Vec v0 = new Vec(start.facing.getFrontOffsetX() * horizontalDistance, 0, start.facing.getFrontOffsetZ() * horizontalDistance);
        Vec v1 = new Vec(-end.facing.getFrontOffsetX() * horizontalDistance, 0, -end.facing.getFrontOffsetZ() * horizontalDistance);
        int samples = Math.max(64, (int) Math.ceil(horizontalDistance * 8.0));
        List<Vec> points = new ArrayList<>(samples + 1);
        double length = 0;
        double minRadius = Double.POSITIVE_INFINITY;
        double maxGrade = 0;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Vec point = quintic(p0, p1, v0, v1, t);
            points.add(point);
            if (i > 0) {
                Vec delta = point.subtract(points.get(i - 1));
                length += delta.length();
                double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                if (horizontal > 1.0E-6) {
                    maxGrade = Math.max(maxGrade, Math.abs(delta.y) / horizontal);
                }
            }
            if (i > 1) {
                Vec a = points.get(i - 1).subtract(points.get(i - 2));
                Vec b = point.subtract(points.get(i - 1));
                double ah = Math.sqrt(a.x * a.x + a.z * a.z);
                double bh = Math.sqrt(b.x * b.x + b.z * b.z);
                if (ah > 1.0E-6 && bh > 1.0E-6) {
                    double cosine = clamp((a.x * b.x + a.z * b.z) / (ah * bh), -1, 1);
                    double angle = Math.acos(cosine);
                    if (angle > 1.0E-5) {
                        minRadius = Math.min(minRadius, ((ah + bh) * 0.5) / angle);
                    }
                }
            }
        }
        if (minRadius < preset.minimumRadius) {
            throw new TunnelBuildException(String.format(Locale.ROOT,
                    "曲线最小半径 %.2f 小于预设限制 %.2f", minRadius, preset.minimumRadius));
        }
        if (maxGrade > preset.maximumGrade) {
            throw new TunnelBuildException(String.format(Locale.ROOT,
                    "最大坡度 %.2f%% 超过预设限制 %.2f%%", maxGrade * 100, preset.maximumGrade * 100));
        }
        List<BlockPos> route = rasterize(points, start.pos, end.pos);
        if (route.size() < 2) {
            throw new TunnelBuildException("无法将曲线转换为连续轨道");
        }
        return new Geometry(route, length, minRadius, maxGrade);
    }

    private static Vec quintic(Vec p0, Vec p1, Vec v0, Vec v1, double t) {
        Vec d = p1.subtract(p0);
        Vec c3 = d.scale(10).subtract(v0.scale(6)).subtract(v1.scale(4));
        Vec c4 = d.scale(-15).add(v0.scale(8)).add(v1.scale(7));
        Vec c5 = d.scale(6).subtract(v0.scale(3)).subtract(v1.scale(3));
        return p0.add(v0.scale(t)).add(c3.scale(t * t * t))
                .add(c4.scale(t * t * t * t)).add(c5.scale(t * t * t * t * t));
    }

    private static List<BlockPos> rasterize(List<Vec> points, BlockPos start, BlockPos end) throws TunnelBuildException {
        List<BlockPos> result = new ArrayList<>();
        BlockPos current = start;
        result.add(current);
        Set<Long> visitedXZ = new HashSet<>();
        visitedXZ.add(xzKey(current.getX(), current.getZ()));
        for (Vec point : points) {
            int targetX = (int) Math.round(point.x);
            int targetZ = (int) Math.round(point.z);
            int targetY = (int) Math.round(point.y);
            while (current.getX() != targetX || current.getZ() != targetZ) {
                int sx = Integer.signum(targetX - current.getX());
                int sz = Integer.signum(targetZ - current.getZ());
                boolean moveX = sx != 0 && (sz == 0 || Math.abs(point.x - current.getX()) >= Math.abs(point.z - current.getZ()));
                int nextX = current.getX() + (moveX ? sx : 0);
                int nextZ = current.getZ() + (moveX ? 0 : sz);
                int nextY = current.getY();
                if (nextY != targetY) {
                    nextY += Integer.signum(targetY - nextY);
                }
                BlockPos next = new BlockPos(nextX, nextY, nextZ);
                long key = xzKey(nextX, nextZ);
                if (visitedXZ.contains(key)) {
                    throw new TunnelBuildException("曲线在方格化后发生自交，请增大标记间距或调整方向");
                }
                visitedXZ.add(key);
                result.add(next);
                current = next;
            }
        }
        if (current.getX() != end.getX() || current.getZ() != end.getZ()) {
            throw new TunnelBuildException("轨道路径没有到达终点");
        }
        if (current.getY() != end.getY()) {
            if (Math.abs(current.getY() - end.getY()) > 1 || result.size() < 2) {
                throw new TunnelBuildException("坡度无法转换为连续的轨道台阶");
            }
            result.set(result.size() - 1, new BlockPos(end.getX(), end.getY(), end.getZ()));
        }
        return result;
    }

    private static int railMeta(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        BlockPos previous = index > 0 ? route.get(index - 1) : null;
        BlockPos next = index + 1 < route.size() ? route.get(index + 1) : null;
        BlockPos higher = null;
        if (next != null && next.getY() > current.getY()) higher = next;
        if (previous != null && previous.getY() > current.getY()) higher = previous;
        if (higher != null) {
            EnumFacing up = horizontalDirection(current, higher);
            switch (up) {
                case EAST: return BlockRailBase.EnumRailDirection.ASCENDING_EAST.getMetadata();
                case WEST: return BlockRailBase.EnumRailDirection.ASCENDING_WEST.getMetadata();
                case NORTH: return BlockRailBase.EnumRailDirection.ASCENDING_NORTH.getMetadata();
                case SOUTH: return BlockRailBase.EnumRailDirection.ASCENDING_SOUTH.getMetadata();
                default: break;
            }
        }
        EnumFacing a = previous == null ? null : horizontalDirection(current, previous);
        EnumFacing b = next == null ? null : horizontalDirection(current, next);
        if (a == null) a = b == null ? EnumFacing.NORTH : b.getOpposite();
        if (b == null) b = a.getOpposite();
        Set<EnumFacing> pair = new HashSet<>();
        pair.add(a);
        pair.add(b);
        if (pair.contains(EnumFacing.NORTH) && pair.contains(EnumFacing.SOUTH)) return 0;
        if (pair.contains(EnumFacing.EAST) && pair.contains(EnumFacing.WEST)) return 1;
        if (pair.contains(EnumFacing.SOUTH) && pair.contains(EnumFacing.EAST)) return 6;
        if (pair.contains(EnumFacing.SOUTH) && pair.contains(EnumFacing.WEST)) return 7;
        if (pair.contains(EnumFacing.NORTH) && pair.contains(EnumFacing.WEST)) return 8;
        if (pair.contains(EnumFacing.NORTH) && pair.contains(EnumFacing.EAST)) return 9;
        return routeFacing(route, index).getAxis() == EnumFacing.Axis.X ? 1 : 0;
    }

    private static IBlockState thirdRailState(boolean yellow, List<BlockPos> route, int index,
                                               EnumFacing pathForward, EnumFacing railSide,
                                               int diagonalType, EnumFacing diagonalFacing,
                                               boolean support)
            throws TunnelBuildException {
        String color = yellow ? "yellow" : "white";
        String id = "nebulaecraft:thirdrail_" + color;
        int grade = gradeDirection(route, index);
        EnumFacing modelFacing = railSide;
        if (isSlopeRailCell(route, index)) {
            EnumFacing ascending = grade > 0 ? pathForward : pathForward.getOpposite();
            if (ascending == railSide.rotateYCCW()) {
                id += "_slope_1";
            } else if (ascending == railSide.rotateY()) {
                id += "_slope_2";
            } else {
                throw new TunnelBuildException("无法匹配第三轨坡道的实际模型方向");
            }
        } else if (diagonalType != 0) {
            if (support) {
                id += "_support_diagonal";
            } else {
                id += diagonalType == 2 ? "_diagonal_2" : "_diagonal_1";
            }
            modelFacing = diagonalFacing;
        } else if (support) {
            id += "_support";
        }
        Block block = resolveBlock(id + "@0");
        return nebulaFacingState(block, modelFacing);
    }

    /**
     * Builds the third-rail model sequence independently from the track-cell indices. A short
     * one-block lateral shift uses the route cell immediately before the first track corner for
     * diagonal_1. The diagonal_2 state belongs to the first corner index but is offset both one
     * block back along the incoming direction and one block toward the lateral shift, directly
     * beside diagonal_1 and beside the first outgoing straight cell. The model order is
     * diagonal_1 then diagonal_2.
     * Continuous 45-degree raster lines retain their uninterrupted alternating diagonal sequence.
     *
     * Each adjacent diagonal_1/diagonal_2 pair consumes one effective spacing unit. If that unit
     * receives a support, only its diagonal_1 cell may use the diagonal support model.
     */
    private static ThirdRailLayout createThirdRailLayout(List<BlockPos> route,
                                                          List<EnumFacing> sectionFacings,
                                                          boolean mirrored, int supportSpacing)
            throws TunnelBuildException {
        int size = route.size();
        int[] diagonalTypes = new int[size];
        EnumFacing[] diagonalFacings = new EnumFacing[size];
        BlockPos[] placementOffsets = new BlockPos[size];
        int thirdRailRouteSide = mirrored ? -1 : 1;

        // Base layout: ordinary corners and continuous 45-degree runs.
        for (int i = 1; i + 1 < size; i++) {
            int routeTurn = turn(route, i);
            if (routeTurn != 0) {
                diagonalTypes[i] = routeTurn == thirdRailRouteSide ? 2 : 1;
                diagonalFacings[i] = cornerFacing(route, i);
            }
        }

        // Re-anchor every one-block lateral-shift unit aligned with the section direction.
        // Consecutive units tile a long 45-degree run as adjacent diagonal_1/diagonal_2 pairs;
        // the section-axis check selects one tessellation and prevents overlapping pairs.
        for (int i = 1; i + 2 < size; i++) {
            BlockPos before = route.get(i - 1);
            BlockPos oldCorner = route.get(i);
            BlockPos newCorner = route.get(i + 1);
            BlockPos after = route.get(i + 2);
            if (before.getY() != oldCorner.getY() || oldCorner.getY() != newCorner.getY()
                    || newCorner.getY() != after.getY()) {
                continue;
            }
            EnumFacing incoming = horizontalDirection(before, oldCorner);
            EnumFacing shift = horizontalDirection(oldCorner, newCorner);
            EnumFacing outgoing = horizontalDirection(newCorner, after);
            if (incoming == null || shift == null || outgoing != incoming
                    || shift.getAxis() == incoming.getAxis()
                    || sectionFacings.get(i).getAxis() != incoming.getAxis()) {
                continue;
            }
            EnumFacing firstFacing = cornerFacing(route, i);
            EnumFacing secondFacing = cornerFacing(route, i + 1);
            diagonalTypes[i + 1] = 0;
            diagonalFacings[i + 1] = null;
            diagonalTypes[i + 2] = 0;
            diagonalFacings[i + 2] = null;
            diagonalTypes[i - 1] = 1;
            diagonalFacings[i - 1] = firstFacing.getOpposite();
            diagonalTypes[i] = 2;
            diagonalFacings[i] = secondFacing;
            // diagonal_2 belongs directly beside diagonal_1: one block back along the incoming
            // direction and one block toward the lateral shift. This also leaves the first
            // outgoing straight third-rail cell untouched.
            placementOffsets[i] = new BlockPos(
                    incoming.getOpposite().getFrontOffsetX() + shift.getFrontOffsetX(),
                    0,
                    incoming.getOpposite().getFrontOffsetZ() + shift.getFrontOffsetZ());
        }

        boolean[] supports = new boolean[size];
        int spacing = Math.max(1, supportSpacing);
        int effectiveIndex = 0;
        for (int i = 0; i < size; ) {
            if (isSlopeRailCell(route, i)) {
                effectiveIndex++;
                i++;
                continue;
            }
            if (diagonalTypes[i] != 0 && i + 1 < size
                    && diagonalTypes[i + 1] != 0
                    && diagonalTypes[i] != diagonalTypes[i + 1]) {
                if (effectiveIndex % spacing == 0) {
                    int diagonalOneIndex = diagonalTypes[i] == 1 ? i : i + 1;
                    supports[diagonalOneIndex] = true;
                }
                effectiveIndex++;
                i += 2;
                continue;
            }
            if (effectiveIndex % spacing == 0 && diagonalTypes[i] != 2) {
                supports[i] = true;
            }
            effectiveIndex++;
            i++;
        }
        return new ThirdRailLayout(diagonalTypes, diagonalFacings, placementOffsets, supports);
    }

    private static IBlockState catenaryState(List<BlockPos> route, int index, EnumFacing facing, boolean support)
            throws TunnelBuildException {
        String id;
        if (support) {
            id = "nebulaecraft:catenary_steel_support";
        } else if (isSlopeRailCell(route, index)) {
            id = "nebulaecraft:catenary_steel_slope";
            facing = gradeDirection(route, index) > 0 ? facing : facing.getOpposite();
        } else if (turn(route, index) != 0) {
            id = "nebulaecraft:catenary_steel_diagonal";
            facing = cornerFacing(route, index);
        } else {
            id = "nebulaecraft:catenary_steel";
        }
        Block block = resolveBlock(id + "@0");
        return nebulaFacingState(block, facing);
    }

    /**
     * Preserve diagonal and slope contact-wire blocks when a support interval lands on either
     * special model. The configured value is used directly as the route-index period. The support
     * is moved to the nearest truly straight wire cell within two route positions. Earlier
     * positions win an equal-distance tie.
     */
    private static Set<Integer> catenarySupportIndices(List<BlockPos> route, int spacing) {
        Set<Integer> supports = new HashSet<>();
        int safeSpacing = Math.max(1, spacing);
        for (int requested = 0; requested < route.size(); requested += safeSpacing) {
            int target = requested;
            if (turn(route, requested) != 0 || isSlopeRailCell(route, requested)) {
                target = -1;
                for (int distance = 1; distance <= 2 && target < 0; distance++) {
                    int before = requested - distance;
                    if (isStraightLevelCatenaryCell(route, before) && !supports.contains(before)) {
                        target = before;
                        break;
                    }
                    int after = requested + distance;
                    if (isStraightLevelCatenaryCell(route, after) && !supports.contains(after)) {
                        target = after;
                    }
                }
            }
            if (target >= 0) {
                supports.add(target);
            }
        }
        return supports;
    }

    private static boolean isStraightLevelCatenaryCell(List<BlockPos> route, int index) {
        return index >= 0 && index < route.size()
                && turn(route, index) == 0 && !isSlopeRailCell(route, index);
    }

    /**
     * NebulaeCraft's generated horizontal blocks use metadata N=0, S=1, W=2, E=3,
     * which is not the vanilla horizontal metadata order. Keep the conversion explicit here;
     * callers pass the facing understood by each model, not a vanilla placement metadata value.
     */
    private static IBlockState nebulaFacingState(Block block, EnumFacing modelFacing)
            throws TunnelBuildException {
        final int meta;
        switch (modelFacing) {
            case NORTH: meta = 0; break;
            case SOUTH: meta = 1; break;
            case WEST: meta = 2; break;
            case EAST: meta = 3; break;
            default: throw new TunnelBuildException("自动生成方块需要水平方向");
        }
        return block.getStateFromMeta(meta);
    }

    /**
     * Vertical slabs and tunnel lights name the direction they face into the free space. Their
     * geometry is physically attached to the opposite side of the occupied block.
     */
    private static IBlockState sideMountedState(Block block, EnumFacing attachmentSide)
            throws TunnelBuildException {
        return nebulaFacingState(block, attachmentSide.getOpposite());
    }

    /** Returns the model-facing value used by the mod's diagonal models for a route corner. */
    private static EnumFacing cornerFacing(List<BlockPos> route, int index) throws TunnelBuildException {
        if (index <= 0 || index + 1 >= route.size()) {
            throw new TunnelBuildException("路线端点不能使用斜线模型");
        }
        BlockPos current = route.get(index);
        EnumFacing previous = horizontalDirection(current, route.get(index - 1));
        EnumFacing next = horizontalDirection(current, route.get(index + 1));
        if (previous == null || next == null || previous.getAxis() == next.getAxis()) {
            throw new TunnelBuildException("无法匹配斜线方块的实际模型方向");
        }
        for (EnumFacing candidate : EnumFacing.HORIZONTALS) {
            if ((candidate == previous && candidate.rotateYCCW() == next)
                    || (candidate == next && candidate.rotateYCCW() == previous)) {
                return candidate;
            }
        }
        throw new TunnelBuildException("无法匹配斜线方块的实际模型方向");
    }

    private static EnumFacing routeFacing(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        if (index + 1 < route.size()) {
            EnumFacing facing = horizontalDirection(current, route.get(index + 1));
            if (facing != null) return facing;
        }
        if (index > 0) {
            EnumFacing facing = horizontalDirection(route.get(index - 1), current);
            if (facing != null) return facing;
        }
        return EnumFacing.NORTH;
    }

    private static List<EnumFacing> sectionFacings(List<BlockPos> route) {
        List<EnumFacing> result = new ArrayList<>(route.size());
        EnumFacing facing = routeFacing(route, 0);
        final int radius = 6;
        for (int i = 0; i < route.size(); i++) {
            BlockPos from = route.get(Math.max(0, i - radius));
            BlockPos to = route.get(Math.min(route.size() - 1, i + radius));
            int dx = to.getX() - from.getX();
            int dz = to.getZ() - from.getZ();
            int alongCurrent = facing.getAxis() == EnumFacing.Axis.X ? Math.abs(dx) : Math.abs(dz);
            int alongOther = facing.getAxis() == EnumFacing.Axis.X ? Math.abs(dz) : Math.abs(dx);
            if (alongOther > alongCurrent * 3 / 2) {
                facing = dominantFacing(dx, dz);
            } else {
                int signed = facing.getAxis() == EnumFacing.Axis.X ? dx : dz;
                if (signed != 0 && Integer.signum(signed) != axisSign(facing)) {
                    facing = facing.getOpposite();
                }
            }
            result.add(facing);
        }
        return result;
    }

    private static EnumFacing dominantFacing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? EnumFacing.WEST : EnumFacing.EAST;
        }
        return dz < 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
    }

    private static int axisSign(EnumFacing facing) {
        return facing.getAxis() == EnumFacing.Axis.X ? facing.getFrontOffsetX() : facing.getFrontOffsetZ();
    }

    private static EnumFacing horizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.signum(to.getX() - from.getX());
        int dz = Integer.signum(to.getZ() - from.getZ());
        if (dx > 0) return EnumFacing.EAST;
        if (dx < 0) return EnumFacing.WEST;
        if (dz > 0) return EnumFacing.SOUTH;
        if (dz < 0) return EnumFacing.NORTH;
        return null;
    }

    private static int turn(List<BlockPos> route, int index) {
        if (index <= 0 || index + 1 >= route.size()) return 0;
        BlockPos a = route.get(index - 1);
        BlockPos b = route.get(index);
        BlockPos c = route.get(index + 1);
        int ax = b.getX() - a.getX();
        int az = b.getZ() - a.getZ();
        int bx = c.getX() - b.getX();
        int bz = c.getZ() - b.getZ();
        return Integer.signum(ax * bz - az * bx);
    }

    private static int gradeDirection(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        if (index + 1 < route.size() && route.get(index + 1).getY() != current.getY()) {
            return Integer.signum(route.get(index + 1).getY() - current.getY());
        }
        if (index > 0 && route.get(index - 1).getY() != current.getY()) {
            return Integer.signum(current.getY() - route.get(index - 1).getY());
        }
        return 0;
    }

    /**
     * A vanilla/Railcraft ascending rail occupies the lower endpoint of a one-block height step.
     * Only that slice receives the eight-layer slope shell; the upper endpoint remains a normal
     * section and naturally meets the raised roof of the lower slice.
     */
    private static boolean isSlopeRailCell(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        return (index > 0 && route.get(index - 1).getY() > current.getY())
                || (index + 1 < route.size() && route.get(index + 1).getY() > current.getY());
    }

    private static BlockPos offset(BlockPos center, int nx, int nz, int lateral, int y) {
        return center.add(nx * lateral, y, nz * lateral);
    }

    private static EnumFacing lateralFacing(int nx, int nz, int sign) {
        return EnumFacing.getFacingFromVector(nx * sign, 0, nz * sign);
    }

    private static boolean isMarker(WorldServer world, BlockPos pos) {
        ResourceLocation name = world.getBlockState(pos).getBlock().getRegistryName();
        return name != null && name.toString().equals("nebulaecraft:autogen_marker");
    }

    private static IBlockState resolveState(String descriptor) throws TunnelBuildException {
        String[] parts = descriptor.split("@", 2);
        Block block = resolveBlock(descriptor);
        int meta = parts.length == 2 ? parseMeta(parts[1], descriptor) : 0;
        return block.getStateFromMeta(meta);
    }

    private static Block resolveBlock(String descriptor) throws TunnelBuildException {
        String id = descriptor.split("@", 2)[0];
        if (id.equals("railcraft:reinforced_track") || id.equals("railcraft:track_reinforced")) {
            id = "railcraft:track_flex_reinforced";
        }
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR && !id.equals("minecraft:air")) {
            throw new TunnelBuildException("找不到所需方块: " + id);
        }
        return block;
    }

    private static int parseMeta(String value, String descriptor) throws TunnelBuildException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new TunnelBuildException("无效方块 metadata: " + descriptor);
        }
    }

    private static int colorMeta(String color) throws TunnelBuildException {
        String normalized = normalizeColor(color);
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equals(normalized)) return i;
        }
        throw new TunnelBuildException("未知 Railcraft 平台颜色: " + color);
    }

    private static String normalizeColor(String color) {
        String normalized = color.toLowerCase(Locale.ENGLISH).replace('-', '_');
        if (normalized.equals("silver") || normalized.equals("lightgray")) return "light_gray";
        if (normalized.equals("lightblue")) return "light_blue";
        return normalized;
    }

    private static void put(Map<Long, PlannedState> states, BlockPos pos, IBlockState state, int priority) {
        long key = pos.toLong();
        PlannedState current = states.get(key);
        if (current == null || priority >= current.priority) {
            states.put(key, new PlannedState(pos, state, priority));
        }
    }

    private static long xzKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class PlannedState {
        final BlockPos pos;
        final IBlockState state;
        final int priority;

        PlannedState(BlockPos pos, IBlockState state, int priority) {
            this.pos = pos;
            this.state = state;
            this.priority = priority;
        }
    }

    private static final class ThirdRailLayout {
        final int[] diagonalTypes;
        final EnumFacing[] diagonalFacings;
        final BlockPos[] placementOffsets;
        final boolean[] supports;

        ThirdRailLayout(int[] diagonalTypes, EnumFacing[] diagonalFacings,
                        BlockPos[] placementOffsets, boolean[] supports) {
            this.diagonalTypes = diagonalTypes;
            this.diagonalFacings = diagonalFacings;
            this.placementOffsets = placementOffsets;
            this.supports = supports;
        }
    }

    private static final class Geometry {
        final List<BlockPos> route;
        final double length;
        final double minimumRadius;
        final double maximumGrade;

        Geometry(List<BlockPos> route, double length, double minimumRadius, double maximumGrade) {
            this.route = route;
            this.length = length;
            this.minimumRadius = minimumRadius;
            this.maximumGrade = maximumGrade;
        }
    }

    private static final class Vec {
        final double x;
        final double y;
        final double z;

        Vec(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
        Vec scale(double scale) { return new Vec(x * scale, y * scale, z * scale); }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
    }
}
