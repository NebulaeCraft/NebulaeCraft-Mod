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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TunnelBuilder {
    private static final int LOCAL_TRANSITION_PATH_LIMIT = 7;
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
        Block tunnelLight = resolveBlock("nebulaecraft:tunnel_light@0");
        IBlockState platformState = platform.getStateFromMeta(colorMeta);

        LinkedHashMap<Long, PlannedState> states = new LinkedHashMap<>();
        List<TunnelPlan.Operation> railFinalizations = new ArrayList<>();
        List<BlockPos> route = geometry.route;
        List<EnumFacing> sectionFacings = geometry.sectionFacings;
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
            IBlockState railState = reinforcedTrack.getStateFromMeta(railMeta(route, i));
            put(states, center.up(), railState, 50);
            // Railcraft recalculates a flex track's shape in onBlockAdded. During the first pass
            // the next route cell does not exist yet, so a requested corner can be changed into
            // a straight rail. Reapply the explicit route shape after every track is present.
            railFinalizations.add(new TunnelPlan.Operation(center.up(), railState, false));

            // The optional colored maintenance platform sits on the intact left floor block.
            put(states, offset(center, nx, nz, -2, 1), platformState, 40);

            if (normalizedPower.startsWith("thirdrail_")) {
                boolean yellow = normalizedPower.endsWith("yellow");
                EnumFacing thirdRailTowardTrack = lateralFacing(nx, nz, -1);
                IBlockState thirdRail = thirdRailState(yellow, route, i, forward,
                        thirdRailTowardTrack, thirdRailLayout.diagonalTypes[i],
                        thirdRailLayout.diagonalFacings[i], thirdRailLayout.supports[i]);
                BlockPos thirdRailPos = offset(center, nx, nz, 1, 1);
                if (thirdRailLayout.placementOffsets[i] != null) {
                    thirdRailPos = thirdRailPos.add(thirdRailLayout.placementOffsets[i]);
                }
                put(states, thirdRailPos, thirdRail, 45);
            } else if (normalizedPower.equals("catenary")) {
                IBlockState catenary = catenaryState(route, i, forward,
                        catenarySupportIndices.contains(i));
                put(states, center.up(catenaryY), catenary, 45);
            }

        }

        Set<Long> clearFootprint = applyTurnSectionTransitions(
                states, route, sectionFacings, concrete, horizontalSlab,
                verticalSlab, sideBed, centerBed, platformState, preset.clearHeight,
                preset.catenaryRecessHeight, mirrored);
        orientVerticalSlabsByCurveTangent(
                states, route, sectionFacings, verticalSlab.getBlock());
        placeTunnelLights(states, route, sectionFacings, clearFootprint,
                tunnelLight, settings.lightSpacing, mirrored);

        if (states.size() > settings.maxChangedBlocks) {
            throw new TunnelBuildException("预计修改方块数超过配置上限 " + settings.maxChangedBlocks);
        }
        List<PlannedState> orderedStates = new ArrayList<>(states.values());
        Collections.sort(orderedStates, Comparator.comparingInt(value -> value.priority));
        List<TunnelPlan.Operation> operations = new ArrayList<>(orderedStates.size());
        for (PlannedState planned : orderedStates) {
            operations.add(new TunnelPlan.Operation(planned.pos, planned.state));
        }
        operations.addAll(railFinalizations);
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
    private static Set<Long> applyTurnSectionTransitions(
            Map<Long, PlannedState> states, List<BlockPos> route,
            List<EnumFacing> sectionFacings, IBlockState concrete,
            IBlockState horizontalSlab, IBlockState verticalSlab,
            IBlockState sideBed, IBlockState centerBed,
            IBlockState platformState, int clearHeight,
            int recessHeight, boolean mirrored)
            throws TunnelBuildException {
        TurnSectionLayout transitionLayout = findTurnSectionLayout(route, sectionFacings);
        List<TurnSectionTransition> transitions = transitionLayout.transitions;
        // Full 3x8 structures remain de-duplicated, but the skipped perpendicular transition still
        // owns real 43/44 floor cells. Merge every candidate into the semantic bed before clearance
        // and wall extraction so reversing the route cannot leave the new-axis bed under a wall.
        TrackbedLayout trackbedLayout = createTrackbedLayout(
                route, transitionLayout.allTransitions);
        Set<Long> clearFootprint = levelRouteClearFootprint(route, sectionFacings);
        for (TurnSectionTransition transition : transitions) {
            addTurnSectionClearFootprint(clearFootprint, transition.before, transition.shift);
            addTurnSectionClearFootprint(clearFootprint, transition.oldCorner, transition.shift);
            addTurnSectionClearFootprint(clearFootprint,
                    transition.projectedOldCenter, transition.shift);
        }
        addTrackbedClearanceFootprint(clearFootprint, trackbedLayout, route);

        for (TurnSectionTransition transition : transitions) {
            putTurnSectionRow(states, transition.before, transition.shift,
                    concrete, horizontalSlab, verticalSlab,
                    sideBed, centerBed, clearHeight, recessHeight,
                    new int[]{-1, 1, 2}, new int[]{0});
            putTurnSectionRow(states, transition.oldCorner, transition.shift,
                    concrete, horizontalSlab, verticalSlab,
                    sideBed, centerBed, clearHeight, recessHeight,
                    new int[]{-1, 2}, new int[]{0, 1});
            putTurnSectionRow(states, transition.projectedOldCenter, transition.shift,
                    concrete, horizontalSlab, verticalSlab,
                    sideBed, centerBed, clearHeight, recessHeight,
                    new int[]{-1, 0, 2}, new int[]{1});
        }

        // The perpendicular candidate omitted from the full shell still carries a real recess
        // pattern. Merge this semantic layer for every candidate; center air outranks side slabs,
        // and side slabs outrank the surrounding concrete in the same way as the full template.
        for (TurnSectionTransition transition : transitionLayout.allTransitions) {
            putTurnCatenaryRow(states, transition.before, transition.shift,
                    concrete, horizontalSlab, clearHeight,
                    new int[]{-1, 1, 2}, new int[]{0});
            putTurnCatenaryRow(states, transition.oldCorner, transition.shift,
                    concrete, horizontalSlab, clearHeight,
                    new int[]{-1, 2}, new int[]{0, 1});
            putTurnCatenaryRow(states, transition.projectedOldCenter, transition.shift,
                    concrete, horizontalSlab, clearHeight,
                    new int[]{-1, 0, 2}, new int[]{1});
        }

        rebuildTurnPlatform(states, transitionLayout.allTransitions, route,
                trackbedLayout, clearFootprint, platformState, mirrored);

        Set<Long> wallBoundary = rebuildTurnShell(
                states, route, sectionFacings, clearFootprint,
                concrete, verticalSlab, clearHeight, recessHeight);
        addTurnWallCornerBackings(states, route, clearFootprint, wallBoundary,
                concrete, clearHeight, recessHeight);
        rebuildContinuousTrackbed(states, trackbedLayout, concrete,
                sideBed, centerBed, clearFootprint);
        return clearFootprint;
    }

    /**
     * Select one non-overlapping tessellation at the point where a long diagonal run changes its
     * section axis. Without the adjacent-index guard, the same two track corners are expanded once
     * as an X-oriented shift and again as a Z-oriented shift; the later template then cuts through
     * the former wall and trackbed.
     */
    private static TurnSectionLayout findTurnSectionLayout(List<BlockPos> route,
                                                            List<EnumFacing> sectionFacings) {
        List<TurnSectionTransition> transitions = new ArrayList<>();
        List<TurnSectionTransition> allTransitions = new ArrayList<>();
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

            TurnSectionTransition candidate = new TurnSectionTransition(i, before, oldCorner,
                    newCorner, after, after.offset(shift.getOpposite()), incoming, shift);
            allTransitions.add(candidate);
            if (!transitions.isEmpty()) {
                TurnSectionTransition previous = transitions.get(transitions.size() - 1);
                if (i == previous.index + 1
                        && incoming.getAxis() != previous.incoming.getAxis()) {
                    // The two perpendicular 3x8 templates overlap at the X/Z section-axis switch.
                    // Keep only the first structural template. The skipped transition still keeps
                    // its trackbed, recess, and platform semantics.
                    continue;
                }
            }
            transitions.add(candidate);
        }
        return new TurnSectionLayout(transitions, allTransitions);
    }

    private static Set<Long> levelRouteClearFootprint(List<BlockPos> route,
                                                       List<EnumFacing> sectionFacings) {
        Set<Long> footprint = new HashSet<>();
        for (int i = 0; i < route.size(); i++) {
            EnumFacing lateral = sectionFacings.get(i).rotateY();
            for (int offset = -2; offset <= 2; offset++) {
                footprint.add(route.get(i).offset(lateral, offset).toLong());
            }
        }
        return footprint;
    }

    private static void addTurnSectionClearFootprint(Set<Long> clearFootprint,
                                                     BlockPos center, EnumFacing shift) {
        for (int offset = -2; offset <= 3; offset++) {
            clearFootprint.add(center.offset(shift, offset).toLong());
        }
    }

    /**
     * The reference floor always leaves one intact concrete cell between either side of the
     * 43/44 trackbed and the wall. A route-graph corner can contribute a side-bed cell outside the
     * section-axis footprint, so adding only the bed cell itself lets the rebuilt boundary touch
     * that 44:0 directly. Dilate the final graph/template bed by one cardinal cell before extracting
     * the wall. Cells beyond the open route ends remain untouched.
     */
    private static void addTrackbedClearanceFootprint(Set<Long> clearFootprint,
                                                       TrackbedLayout trackbedLayout,
                                                       List<BlockPos> route) {
        Set<Long> trackbedCells = new HashSet<>(trackbedLayout.centerCells);
        trackbedCells.addAll(trackbedLayout.sideCells.keySet());
        for (long packed : trackbedCells) {
            BlockPos trackbed = BlockPos.fromLong(packed);
            clearFootprint.add(packed);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos floor = trackbed.offset(direction);
                if (!isBeyondOpenRouteEnd(floor, route)) {
                    clearFootprint.add(floor.toLong());
                }
            }
        }
    }

    /**
     * Rebuild the complete curve shell from the union of every normal and widened clear section.
     * The same final footprint supplies the floor and two ceiling layers. Only cardinal neighbours
     * form the regular wall: adding the complete diagonal ring would create a full-height column at
     * every inside step of the curve, where it visibly projects into the tunnel. True outside-corner
     * backings are derived from the completed cardinal boundary separately below.
     */
    private static Set<Long> rebuildTurnShell(
                                                Map<Long, PlannedState> states,
                                                List<BlockPos> route,
                                                List<EnumFacing> sectionFacings,
                                                Set<Long> clearFootprint,
                                                IBlockState concrete,
                                                IBlockState verticalSlab,
                                                int clearHeight, int recessHeight)
            throws TunnelBuildException {
        int roofY = clearHeight + 1 + recessHeight;
        Set<Long> originalVerticalSlabs = new HashSet<>();
        for (Map.Entry<Long, PlannedState> entry : states.entrySet()) {
            if (entry.getValue().state.getBlock() == verticalSlab.getBlock()) {
                originalVerticalSlabs.add(entry.getKey());
            }
        }
        Set<Long> boundary = new HashSet<>();
        for (long packed : clearFootprint) {
            BlockPos clear = BlockPos.fromLong(packed);
            // Floor, walls, and ceiling must all come from the same final semantic clearance.
            // A de-duplicated axis-switch template still contributes trackbed clearance; relying
            // only on retained full templates for the two ceiling layers leaves that clearance
            // uncapped. These defaults sit below every explicit recess air/slab/wire state.
            int routeIndex = closestRouteIndex(clear, route);
            int slopeLift = isSlopeRailCell(route, routeIndex) ? 1 : 0;
            put(states, clear.up(clearHeight + 1 + slopeLift), concrete, 9);
            put(states, clear.up(roofY + slopeLift), concrete, 9);

            // The entire horizontal opening is authoritative. Clear remnants left by differently
            // oriented normal sections before reconstructing its outer boundary.
            for (int y = 1; y <= clearHeight; y++) {
                put(states, clear.up(y), Blocks.AIR.getDefaultState(), 37);
            }
            put(states, clear, concrete, 38);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos candidate = clear.offset(direction);
                if (!containsClearFootprintAtGrade(candidate, clearFootprint)
                        && !isBeyondOpenRouteEnd(candidate, route)) {
                    boundary.add(candidate.toLong());
                }
            }
        }

        for (long packed : boundary) {
            BlockPos wall = BlockPos.fromLong(packed);
            List<EnumFacing> inwardDirections = new ArrayList<>();
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                if (clearFootprint.contains(wall.offset(direction).toLong())) {
                    inwardDirections.add(direction);
                }
            }
            EnumFacing inward = preferredWallInwardDirection(
                    wall, inwardDirections, route, sectionFacings);
            IBlockState middleWallState = sideMountedState(
                    verticalSlab.getBlock(), inward.getOpposite());
            boolean slopeTop = originalVerticalSlabs.contains(
                    wall.up(clearHeight).toLong());
            for (int y = 0; y <= roofY; y++) {
                BlockPos target = wall.up(y);
                if (insideClearVolume(target, clearFootprint, clearHeight)) {
                    continue;
                }
                IBlockState wallState = concrete;
                if (slopeTop && y == clearHeight) {
                    wallState = middleWallState;
                } else if (y >= 2 && y < clearHeight) {
                    wallState = middleWallState;
                }
                put(states, target, wallState, 38);
            }
        }
        return boundary;
    }

    private static boolean containsClearFootprintAtGrade(
            BlockPos pos, Set<Long> clearFootprint) {
        return clearFootprint.contains(pos.toLong())
                || clearFootprint.contains(pos.up().toLong())
                || clearFootprint.contains(pos.down().toLong());
    }

    private static boolean insideClearVolume(BlockPos pos,
                                             Set<Long> clearFootprint,
                                             int clearHeight) {
        for (int down = 0; down <= clearHeight; down++) {
            if (clearFootprint.contains(pos.down(down).toLong())) {
                return true;
            }
        }
        return false;
    }

    private static int boundaryPathDistance(BlockPos from, BlockPos to) {
        return Math.max(Math.abs(from.getX() - to.getX()),
                Math.abs(from.getZ() - to.getZ()));
    }

    private static List<BlockPos> reconstructBoundaryPath(Map<Long, Long> previous,
                                                           BlockPos start, BlockPos end) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos current = end;
        reversed.add(current);
        while (!current.equals(start)) {
            Long parent = previous.get(current.toLong());
            if (parent == null) {
                return Collections.emptyList();
            }
            current = BlockPos.fromLong(parent);
            reversed.add(current);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Orient every vertical slab from the smooth center-curve tangent at the closest route point.
     * A tangent closer to X uses a NORTH/SOUTH normal; one closer to Z uses an EAST/WEST normal.
     * Stair-step boundary cells can have only an along-track clear neighbour, so adjacency alone
     * must not rotate their slab by ninety degrees. Prefer a matching clear neighbour when one is
     * available, otherwise choose the tangent normal that points toward the route.
     */
    private static EnumFacing preferredWallInwardDirection(
            BlockPos wall, List<EnumFacing> inwardDirections,
            List<BlockPos> route, List<EnumFacing> sectionFacings) {
        int routeIndex = closestHorizontalRouteIndex(wall, route);
        EnumFacing.Axis trackAxis = sectionFacings.get(routeIndex).getAxis();
        List<EnumFacing> tangentNormals = new ArrayList<>(2);
        for (EnumFacing inward : inwardDirections) {
            if (inward.getAxis() != trackAxis) {
                tangentNormals.add(inward);
            }
        }
        if (!tangentNormals.isEmpty()) {
            return closestHorizontalRouteDirection(wall, tangentNormals, route);
        }
        return curveNormalTowardRoute(wall, routeIndex, route, sectionFacings);
    }

    private static EnumFacing curveNormalTowardRoute(
            BlockPos wall, int routeIndex, List<BlockPos> route,
            List<EnumFacing> sectionFacings) {
        EnumFacing firstNormal = sectionFacings.get(routeIndex).rotateY();
        List<EnumFacing> tangentNormals = new ArrayList<>(2);
        tangentNormals.add(firstNormal);
        tangentNormals.add(firstNormal.getOpposite());
        return closestHorizontalRouteDirection(wall, tangentNormals, route);
    }

    /** Apply the tangent-axis rule to every final slab, including open-end/template remnants. */
    private static void orientVerticalSlabsByCurveTangent(
            Map<Long, PlannedState> states, List<BlockPos> route,
            List<EnumFacing> sectionFacings, Block verticalSlab)
            throws TunnelBuildException {
        for (Map.Entry<Long, PlannedState> entry : states.entrySet()) {
            PlannedState planned = entry.getValue();
            if (planned.state.getBlock() != verticalSlab) {
                continue;
            }
            int routeIndex = closestHorizontalRouteIndex(planned.pos, route);
            EnumFacing inward = curveNormalTowardRoute(
                    planned.pos, routeIndex, route, sectionFacings);
            IBlockState oriented = sideMountedState(verticalSlab, inward.getOpposite());
            entry.setValue(new PlannedState(planned.pos, oriented, planned.priority));
        }
    }

    private static EnumFacing closestHorizontalRouteDirection(
            BlockPos wall, List<EnumFacing> directions, List<BlockPos> route) {
        EnumFacing best = directions.get(0);
        long bestDistance = Long.MAX_VALUE;
        for (EnumFacing direction : directions) {
            BlockPos inside = wall.offset(direction);
            long distance = horizontalDistanceToRoute(inside, route);
            if (distance < bestDistance) {
                best = direction;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int distanceToRoute(BlockPos pos, List<BlockPos> route) {
        return routeDistanceAtIndex(pos, route, closestRouteIndex(pos, route));
    }

    private static int closestRouteIndex(BlockPos pos, List<BlockPos> route) {
        int bestIndex = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            int distance = routeDistanceAtIndex(pos, route, i);
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int closestHorizontalRouteIndex(BlockPos pos, List<BlockPos> route) {
        int bestIndex = 0;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            BlockPos routePos = route.get(i);
            long dx = pos.getX() - (long) routePos.getX();
            long dz = pos.getZ() - (long) routePos.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static long horizontalDistanceToRoute(BlockPos pos, List<BlockPos> route) {
        BlockPos routePos = route.get(closestHorizontalRouteIndex(pos, route));
        long dx = pos.getX() - (long) routePos.getX();
        long dz = pos.getZ() - (long) routePos.getZ();
        return dx * dx + dz * dz;
    }

    private static int routeDistanceAtIndex(BlockPos pos, List<BlockPos> route, int index) {
        BlockPos routePos = route.get(index);
        return Math.abs(pos.getX() - routePos.getX())
                + Math.abs(pos.getY() - routePos.getY())
                + Math.abs(pos.getZ() - routePos.getZ());
    }

    /**
     * Fill the true outside corner behind two perpendicular cells of the final wall boundary. A
     * fixed transition-template coordinate misses corners introduced by overlapping shifts and by
     * the X/Z axis switch; filling every diagonal neighbour, on the other hand, creates inside
     * piers. Requiring both orthogonal elbows to be real final wall cells identifies only the
     * backing column needed to close the visible seam.
     */
    private static void addTurnWallCornerBackings(
                                               Map<Long, PlannedState> states,
                                               List<BlockPos> route,
                                               Set<Long> clearFootprint,
                                               Set<Long> wallBoundary,
                                               IBlockState concrete,
                                               int clearHeight, int recessHeight) {
        int roofY = clearHeight + 1 + recessHeight;
        Set<Long> backings = new HashSet<>();
        for (long packed : clearFootprint) {
            BlockPos clear = BlockPos.fromLong(packed);
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    BlockPos corner = clear.add(dx, 0, dz);
                    BlockPos xWall = clear.add(dx, 0, 0);
                    BlockPos zWall = clear.add(0, 0, dz);
                    if (!containsClearFootprintAtGrade(corner, clearFootprint)
                            && !wallBoundary.contains(corner.toLong())
                            && wallBoundary.contains(xWall.toLong())
                            && wallBoundary.contains(zWall.toLong())
                            && !isBeyondOpenRouteEnd(corner, route)) {
                        backings.add(corner.toLong());
                    }
                }
            }
        }
        for (long packed : backings) {
            BlockPos corner = BlockPos.fromLong(packed);
            for (int y = 0; y <= roofY; y++) {
                put(states, corner.up(y), concrete, 9);
            }
        }
    }

    private static boolean isBeyondOpenRouteEnd(BlockPos pos, List<BlockPos> route) {
        if (route.size() < 2) {
            return false;
        }
        BlockPos start = route.get(0);
        EnumFacing startForward = horizontalDirection(start, route.get(1));
        if (startForward != null && pos.getY() == start.getY()
                && horizontalDot(start, pos, startForward) < 0) {
            return true;
        }
        BlockPos end = route.get(route.size() - 1);
        EnumFacing endForward = horizontalDirection(route.get(route.size() - 2), end);
        return endForward != null && pos.getY() == end.getY()
                && horizontalDot(end, pos, endForward) > 0;
    }

    private static int horizontalDot(BlockPos origin, BlockPos pos, EnumFacing facing) {
        return (pos.getX() - origin.getX()) * facing.getFrontOffsetX()
                + (pos.getZ() - origin.getZ()) * facing.getFrontOffsetZ();
    }

    /**
     * Plan the complete bed before the wall. At a corner both connected segment axes contribute
     * their lateral cells, so consecutive shifts and the X/Z axis change produce a continuous band
     * without relying on the section-facing axis. The widened transition's extra 43/44 cells are
     * included here as well; this makes "trackbed is always inside clearance" an explicit invariant.
     */
    private static TrackbedLayout createTrackbedLayout(
            List<BlockPos> route, List<TurnSectionTransition> transitions) {
        Set<Long> routeCells = new HashSet<>();
        Set<Long> centerCells = new HashSet<>();
        Map<Long, Boolean> sideCells = new LinkedHashMap<>();
        for (BlockPos center : route) {
            routeCells.add(center.toLong());
            centerCells.add(center.toLong());
        }
        for (int i = 0; i < route.size(); i++) {
            BlockPos center = route.get(i);
            boolean solidSlopeBed = isSlopeRailCell(route, i);
            Set<EnumFacing> connections = new HashSet<>();
            if (i > 0) {
                EnumFacing connection = horizontalDirection(center, route.get(i - 1));
                if (connection != null) {
                    connections.add(connection);
                }
            }
            if (i + 1 < route.size()) {
                EnumFacing connection = horizontalDirection(center, route.get(i + 1));
                if (connection != null) {
                    connections.add(connection);
                }
            }
            for (EnumFacing connection : connections) {
                BlockPos left = center.offset(connection.rotateYCCW());
                BlockPos right = center.offset(connection.rotateY());
                if (!routeCells.contains(left.toLong())) {
                    sideCells.put(left.toLong(),
                            solidSlopeBed || Boolean.TRUE.equals(sideCells.get(left.toLong())));
                }
                if (!routeCells.contains(right.toLong())) {
                    sideCells.put(right.toLong(),
                            solidSlopeBed || Boolean.TRUE.equals(sideCells.get(right.toLong())));
                }
            }
        }

        for (TurnSectionTransition transition : transitions) {
            addTurnTrackbedLayoutRow(sideCells, centerCells,
                    transition.before, transition.shift,
                    new int[]{-1, 1, 2}, new int[]{0});
            addTurnTrackbedLayoutRow(sideCells, centerCells,
                    transition.oldCorner, transition.shift,
                    new int[]{-1, 2}, new int[]{0, 1});
            addTurnTrackbedLayoutRow(sideCells, centerCells,
                    transition.projectedOldCenter, transition.shift,
                    new int[]{-1, 0, 2}, new int[]{1});
        }
        for (long packed : centerCells) {
            sideCells.remove(packed);
        }
        connectSideTrackbedCardinally(sideCells, centerCells, route);
        return new TrackbedLayout(centerCells, sideCells);
    }

    /**
     * A diagonal rail shift can leave the two 44:0 lanes touching only at one corner. Visually that
     * is a full-block break because slabs need a shared edge. For each pair of diagonal side-lane
     * components, add the only orthogonal elbow that is outside the 43:8 center bed. This preserves
     * the two one-block-wide lanes and makes their cardinal topology continuous.
     */
    private static void connectSideTrackbedCardinally(
            Map<Long, Boolean> sideCells, Set<Long> centerCells,
            List<BlockPos> route) {
        Map<Long, Integer> componentIds = cardinalComponentIds(sideCells.keySet());
        int componentCount = 0;
        for (int component : componentIds.values()) {
            componentCount = Math.max(componentCount, component + 1);
        }
        int[] parents = new int[componentCount];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = i;
        }

        List<SideTrackbedBridge> candidates = new ArrayList<>();
        List<Long> orderedCells = new ArrayList<>(sideCells.keySet());
        Collections.sort(orderedCells);
        for (long packed : orderedCells) {
            BlockPos first = BlockPos.fromLong(packed);
            int firstComponent = componentIds.get(packed);
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    BlockPos second = first.add(dx, 0, dz);
                    Integer secondComponent = componentIds.get(second.toLong());
                    if (secondComponent == null || firstComponent == secondComponent) {
                        continue;
                    }
                    int low = Math.min(firstComponent, secondComponent);
                    int high = Math.max(firstComponent, secondComponent);

                    BlockPos xThenZ = new BlockPos(
                            second.getX(), first.getY(), first.getZ());
                    BlockPos zThenX = new BlockPos(
                            first.getX(), first.getY(), second.getZ());
                    boolean firstElbowCenter = centerCells.contains(xThenZ.toLong());
                    boolean secondElbowCenter = centerCells.contains(zThenX.toLong());
                    if (firstElbowCenter == secondElbowCenter) {
                        continue;
                    }
                    BlockPos bridge = firstElbowCenter ? zThenX : xThenZ;
                    if (centerCells.contains(bridge.toLong())
                            || sideCells.containsKey(bridge.toLong())
                            || isBeyondOpenRouteEnd(bridge, route)) {
                        continue;
                    }
                    boolean solid = Boolean.TRUE.equals(sideCells.get(first.toLong()))
                            || Boolean.TRUE.equals(sideCells.get(second.toLong()));
                    candidates.add(new SideTrackbedBridge(
                            low, high, bridge, solid));
                }
            }
        }

        Collections.sort(candidates, Comparator.comparingLong(
                value -> value.pos.toLong()));
        for (SideTrackbedBridge candidate : candidates) {
            int firstRoot = componentRoot(parents, candidate.firstComponent);
            int secondRoot = componentRoot(parents, candidate.secondComponent);
            if (firstRoot == secondRoot) {
                continue;
            }
            parents[firstRoot] = secondRoot;
            sideCells.put(candidate.pos.toLong(), candidate.solid);
        }
    }

    private static Map<Long, Integer> cardinalComponentIds(Set<Long> cells) {
        Map<Long, Integer> result = new HashMap<>();
        Set<Long> remaining = new HashSet<>(cells);
        int component = 0;
        while (!remaining.isEmpty()) {
            long start = remaining.iterator().next();
            remaining.remove(start);
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(BlockPos.fromLong(start));
            result.put(start, component);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                    BlockPos next = current.offset(direction);
                    long packed = next.toLong();
                    if (remaining.remove(packed)) {
                        result.put(packed, component);
                        queue.addLast(next);
                    }
                }
            }
            component++;
        }
        return result;
    }

    private static int componentRoot(int[] parents, int component) {
        int root = component;
        while (parents[root] != root) {
            root = parents[root];
        }
        while (parents[component] != component) {
            int parent = parents[component];
            parents[component] = root;
            component = parent;
        }
        return root;
    }

    private static void addTurnTrackbedLayoutRow(Map<Long, Boolean> sideCells,
                                                  Set<Long> centerCells,
                                                  BlockPos center, EnumFacing shift,
                                                  int[] sideOffsets, int[] centerOffsets) {
        for (int offset : sideOffsets) {
            sideCells.put(center.offset(shift, offset).toLong(), false);
        }
        for (int offset : centerOffsets) {
            centerCells.add(center.offset(shift, offset).toLong());
        }
    }

    private static void rebuildContinuousTrackbed(Map<Long, PlannedState> states,
                                                   TrackbedLayout layout,
                                                   IBlockState concrete,
                                                   IBlockState sideBed,
                                                   IBlockState centerBed,
                                                   Set<Long> clearFootprint) {
        for (long packed : clearFootprint) {
            put(states, BlockPos.fromLong(packed), concrete, 38);
        }
        for (Map.Entry<Long, Boolean> entry : layout.sideCells.entrySet()) {
            put(states, BlockPos.fromLong(entry.getKey()),
                    entry.getValue() ? centerBed : sideBed, 39);
        }
        for (long packed : layout.centerCells) {
            put(states, BlockPos.fromLong(packed), centerBed, 40);
        }
    }

    /**
     * Rebuild the platform as one ordered offset lane. Structural transition templates are
     * de-duplicated at an X/Z axis switch, but their facility anchors must not be. After invalid
     * trackbed-overlapping cells are removed, only anchor pairs not already joined by the existing
     * 8-neighbour platform lane receive a short diagonal bridge. Platform cells reserve clearance
     * before the wall is extracted instead of being allowed to replace the trackbed-side floor.
     */
    private static void rebuildTurnPlatform(Map<Long, PlannedState> states,
                                            List<TurnSectionTransition> transitions,
                                            List<BlockPos> route,
                                            TrackbedLayout trackbedLayout,
                                            Set<Long> clearFootprint,
                                            IBlockState platformState,
                                            boolean mirrored)
            throws TunnelBuildException {
        List<TurnPlatformAdjustment> adjustments = new ArrayList<>();
        for (TurnSectionTransition transition : transitions) {
            TurnPlatformAdjustment adjustment = turnPlatformAdjustment(transition, mirrored);
            adjustments.add(adjustment);
            for (BlockPos removed : adjustment.removed) {
                put(states, removed, Blocks.AIR.getDefaultState(), 70);
            }
            put(states, adjustment.added, platformState, 71);
        }

        Set<Long> platformCells = new HashSet<>();
        List<PlannedState> plannedStates = new ArrayList<>(states.values());
        for (PlannedState planned : plannedStates) {
            if (planned.state.getBlock() != platformState.getBlock()) {
                continue;
            }
            if (trackbedLayout.contains(planned.pos.down())) {
                put(states, planned.pos, Blocks.AIR.getDefaultState(), 72);
            } else {
                platformCells.add(planned.pos.toLong());
            }
        }

        for (int i = 1; i < adjustments.size(); i++) {
            BlockPos from = adjustments.get(i - 1).added;
            BlockPos to = adjustments.get(i).added;
            if (from.getY() != to.getY()
                    || platformCellsConnected(platformCells, from, to)) {
                continue;
            }
            List<BlockPos> bridge = findSafePlatformBridge(
                    states, from, to, route, trackbedLayout,
                    clearFootprint, platformState);
            if (bridge.isEmpty()) {
                throw new TunnelBuildException("金属平台无法在不侵占道床的情况下连续连接");
            }
            for (BlockPos platform : bridge) {
                put(states, platform, platformState, 71);
                platformCells.add(platform.toLong());
            }
            for (int j = 1; j < bridge.size(); j++) {
                reservePlatformElbow(clearFootprint, route,
                        bridge.get(j - 1), bridge.get(j));
            }
        }

        if (!platformCellsFormContinuousLane(platformCells)) {
            throw new TunnelBuildException("金属平台无法在不侵占道床的情况下连续连接");
        }
        for (long packed : platformCells) {
            clearFootprint.add(BlockPos.fromLong(packed).down().toLong());
        }
    }

    private static boolean platformCellsConnected(Set<Long> platformCells,
                                                   BlockPos start, BlockPos end) {
        if (!platformCells.contains(start.toLong())
                || !platformCells.contains(end.toLong())) {
            return false;
        }
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(start);
        visited.add(start.toLong());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (current.equals(end)) {
                return true;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos next = current.add(dx, 0, dz);
                    long packed = next.toLong();
                    if (platformCells.contains(packed) && visited.add(packed)) {
                        queue.addLast(next);
                    }
                }
            }
        }
        return false;
    }

    private static boolean platformCellsFormContinuousLane(Set<Long> platformCells) {
        if (platformCells.isEmpty()) {
            return true;
        }
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        BlockPos start = BlockPos.fromLong(platformCells.iterator().next());
        queue.add(start);
        visited.add(start.toLong());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.add(dx, dy, dz);
                        long packed = next.toLong();
                        if (platformCells.contains(packed) && visited.add(packed)) {
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
        return visited.size() == platformCells.size();
    }

    private static TurnPlatformAdjustment turnPlatformAdjustment(
            TurnSectionTransition transition, boolean mirrored) {
        BlockPos before = transition.before;
        BlockPos oldCorner = transition.oldCorner;
        BlockPos newCorner = transition.newCorner;
        BlockPos after = transition.after;
        EnumFacing incoming = transition.incoming;
        EnumFacing shift = transition.shift;
        EnumFacing platformSide = mirrored ? incoming.rotateY() : incoming.rotateYCCW();
        List<BlockPos> removed = new ArrayList<>();
        BlockPos added;
        if (shift == platformSide) {
            removed.add(platformPosition(before, incoming, mirrored));
            removed.add(platformPosition(oldCorner, incoming, mirrored));
            added = platformPosition(before.offset(shift), incoming, mirrored);
        } else {
            removed.add(platformPosition(newCorner, incoming, mirrored));
            removed.add(platformPosition(after, incoming, mirrored));
            added = platformPosition(after.offset(shift.getOpposite()), incoming, mirrored);
        }
        return new TurnPlatformAdjustment(removed, added);
    }

    /**
     * Connect only consecutive transition anchors, inside their small local corridor. A fixed
     * rounded line has two equally short phases; choosing the wrong phase can cross one 43/44 cell
     * and cause the complete bridge to be discarded. Bounded 8-neighbour search selects the short
     * phase that is already inside final clearance and outside the semantic trackbed.
     */
    private static List<BlockPos> findSafePlatformBridge(
            Map<Long, PlannedState> states, BlockPos from, BlockPos to,
            List<BlockPos> route, TrackbedLayout trackbedLayout,
            Set<Long> clearFootprint, IBlockState platformState) {
        if (from.getY() != to.getY()) {
            return Collections.emptyList();
        }
        int shortest = boundaryPathDistance(from, to);
        if (shortest > LOCAL_TRANSITION_PATH_LIMIT
                || !isSafePlatformBridgeCell(states, from, route, trackbedLayout,
                clearFootprint, platformState)
                || !isSafePlatformBridgeCell(states, to, route, trackbedLayout,
                clearFootprint, platformState)) {
            return Collections.emptyList();
        }

        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        int maxDepth = shortest + 2;
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Map<Long, Long> previous = new HashMap<>();
        Map<Long, Integer> depths = new HashMap<>();
        queue.add(from);
        visited.add(from.toLong());
        depths.put(from.toLong(), 0);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            int depth = depths.get(current.toLong());
            if (depth >= maxDepth) {
                continue;
            }
            List<BlockPos> neighbours = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos next = current.add(dx, 0, dz);
                    long packed = next.toLong();
                    if (next.getX() < minX || next.getX() > maxX
                            || next.getZ() < minZ || next.getZ() > maxZ
                            || visited.contains(packed)
                            || !isSafePlatformBridgeCell(states, next, route,
                            trackbedLayout, clearFootprint, platformState)
                            || !platformDiagonalStepHasClearElbow(
                            current, next, clearFootprint)) {
                        continue;
                    }
                    neighbours.add(next);
                }
            }
            Collections.sort(neighbours, Comparator.comparingInt(
                    value -> boundaryPathDistance(value, to)));
            for (BlockPos next : neighbours) {
                long packed = next.toLong();
                visited.add(packed);
                previous.put(packed, current.toLong());
                depths.put(packed, depth + 1);
                if (next.equals(to)) {
                    return reconstructBoundaryPath(previous, from, to);
                }
                queue.addLast(next);
            }
        }
        return Collections.emptyList();
    }

    private static boolean isSafePlatformBridgeCell(
            Map<Long, PlannedState> states, BlockPos platform,
            List<BlockPos> route, TrackbedLayout trackbedLayout,
            Set<Long> clearFootprint, IBlockState platformState) {
        BlockPos floor = platform.down();
        int routeDistance = distanceToRoute(floor, route);
        if (!clearFootprint.contains(floor.toLong())
                || trackbedLayout.contains(floor)
                || isBeyondOpenRouteEnd(floor, route)
                || routeDistance < 2 || routeDistance > 3) {
            return false;
        }
        PlannedState existing = states.get(platform.toLong());
        return existing == null || existing.priority < 45
                || existing.state.getBlock() == Blocks.AIR
                || existing.state.getBlock() == platformState.getBlock();
    }

    private static boolean platformDiagonalStepHasClearElbow(
            BlockPos from, BlockPos to, Set<Long> clearFootprint) {
        if (from.getX() == to.getX() || from.getZ() == to.getZ()) {
            return true;
        }
        BlockPos xThenZ = new BlockPos(to.getX(), from.getY(), from.getZ()).down();
        BlockPos zThenX = new BlockPos(from.getX(), from.getY(), to.getZ()).down();
        return clearFootprint.contains(xThenZ.toLong())
                || clearFootprint.contains(zThenX.toLong());
    }

    private static void reservePlatformElbow(Set<Long> clearFootprint,
                                             List<BlockPos> route,
                                             BlockPos from, BlockPos to) {
        if (from.getX() == to.getX() || from.getZ() == to.getZ()) {
            return;
        }
        BlockPos xThenZ = new BlockPos(to.getX(), from.getY(), from.getZ()).down();
        BlockPos zThenX = new BlockPos(from.getX(), from.getY(), to.getZ()).down();
        int firstDistance = distanceToRoute(xThenZ, route);
        int secondDistance = distanceToRoute(zThenX, route);
        boolean firstClear = clearFootprint.contains(xThenZ.toLong());
        boolean secondClear = clearFootprint.contains(zThenX.toLong());
        BlockPos elbow;
        if (firstClear != secondClear) {
            elbow = firstClear ? xThenZ : zThenX;
        } else if (firstDistance != secondDistance) {
            elbow = firstDistance < secondDistance ? xThenZ : zThenX;
        } else if (firstClear) {
            elbow = xThenZ;
        } else {
            elbow = zThenX;
        }
        clearFootprint.add(elbow.toLong());
    }

    private static BlockPos platformPosition(BlockPos center, EnumFacing forward, boolean mirrored) {
        EnumFacing platformSide = mirrored ? forward.rotateY() : forward.rotateYCCW();
        return center.offset(platformSide, 2).up();
    }

    /** Place each light in the last clear cell before the final platform-side wall. */
    private static void placeTunnelLights(Map<Long, PlannedState> states,
                                          List<BlockPos> route,
                                          List<EnumFacing> sectionFacings,
                                          Set<Long> clearFootprint,
                                          Block tunnelLight,
                                          int spacing,
                                          boolean mirrored)
            throws TunnelBuildException {
        int safeSpacing = Math.max(1, spacing);
        for (int i = 0; i < route.size(); i += safeSpacing) {
            EnumFacing forward = sectionFacings.get(i);
            EnumFacing outward = mirrored ? forward.rotateY() : forward.rotateYCCW();
            BlockPos lightFloor = route.get(i);
            BlockPos next = lightFloor.offset(outward);
            while (clearFootprint.contains(next.toLong())) {
                lightFloor = next;
                next = lightFloor.offset(outward);
            }
            IBlockState lightState = sideMountedState(tunnelLight, outward);
            int lightY = isSlopeRailCell(route, i) ? 5 : 4;
            put(states, lightFloor.up(lightY), lightState, 60);
        }
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

    private static void putTurnCatenaryRow(Map<Long, PlannedState> states,
                                            BlockPos center, EnumFacing shift,
                                            IBlockState concrete, IBlockState horizontalSlab,
                                            int clearHeight,
                                            int[] sideOffsets, int[] centerOffsets) {
        int catenaryY = clearHeight + 1;
        for (int offset = -3; offset <= 4; offset++) {
            put(states, center.offset(shift, offset).up(catenaryY), concrete, 35);
        }
        for (int offset : sideOffsets) {
            put(states, center.offset(shift, offset).up(catenaryY), horizontalSlab, 36);
        }
        for (int offset : centerOffsets) {
            put(states, center.offset(shift, offset).up(catenaryY),
                    Blocks.AIR.getDefaultState(), 37);
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
        RasterizedRoute rasterized = rasterize(points, start.pos, end.pos);
        if (rasterized.positions.size() < 2) {
            throw new TunnelBuildException("无法将曲线转换为连续轨道");
        }
        List<EnumFacing> sectionFacings = curveTangentFacings(
                rasterized.parameters, p0, p1, v0, v1);
        return new Geometry(rasterized.positions, sectionFacings,
                length, minRadius, maxGrade);
    }

    private static Vec quintic(Vec p0, Vec p1, Vec v0, Vec v1, double t) {
        Vec d = p1.subtract(p0);
        Vec c3 = d.scale(10).subtract(v0.scale(6)).subtract(v1.scale(4));
        Vec c4 = d.scale(-15).add(v0.scale(8)).add(v1.scale(7));
        Vec c5 = d.scale(6).subtract(v0.scale(3)).subtract(v1.scale(3));
        return p0.add(v0.scale(t)).add(c3.scale(t * t * t))
                .add(c4.scale(t * t * t * t)).add(c5.scale(t * t * t * t * t));
    }

    private static Vec quinticTangent(Vec p0, Vec p1, Vec v0, Vec v1, double t) {
        Vec d = p1.subtract(p0);
        Vec c3 = d.scale(10).subtract(v0.scale(6)).subtract(v1.scale(4));
        Vec c4 = d.scale(-15).add(v0.scale(8)).add(v1.scale(7));
        Vec c5 = d.scale(6).subtract(v0.scale(3)).subtract(v1.scale(3));
        return v0.add(c3.scale(3 * t * t))
                .add(c4.scale(4 * t * t * t))
                .add(c5.scale(5 * t * t * t * t));
    }

    private static List<EnumFacing> curveTangentFacings(
            List<Double> parameters, Vec p0, Vec p1, Vec v0, Vec v1) {
        List<EnumFacing> facings = new ArrayList<>(parameters.size());
        EnumFacing previous = dominantFacing(v0.x, v0.z);
        for (double parameter : parameters) {
            Vec tangent = quinticTangent(p0, p1, v0, v1, parameter);
            if (Math.abs(tangent.x) > 1.0E-8 || Math.abs(tangent.z) > 1.0E-8) {
                previous = dominantFacing(tangent.x, tangent.z);
            }
            facings.add(previous);
        }
        return facings;
    }

    private static RasterizedRoute rasterize(List<Vec> points, BlockPos start, BlockPos end)
            throws TunnelBuildException {
        List<BlockPos> result = new ArrayList<>();
        List<Double> parameters = new ArrayList<>();
        BlockPos current = start;
        result.add(current);
        parameters.add(0.0);
        Set<Long> visitedXZ = new HashSet<>();
        visitedXZ.add(xzKey(current.getX(), current.getZ()));
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            Vec point = points.get(pointIndex);
            double parameter = pointIndex / (double) (points.size() - 1);
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
                parameters.add(parameter);
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
        return new RasterizedRoute(result, parameters);
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
                                               EnumFacing curveForward, EnumFacing railSide,
                                               int diagonalType, EnumFacing diagonalFacing,
                                               boolean support)
            throws TunnelBuildException {
        String color = yellow ? "yellow" : "white";
        String id = "nebulaecraft:thirdrail_" + color;
        int grade = gradeDirection(route, index);
        EnumFacing modelFacing = railSide;
        if (isSlopeRailCell(route, index)) {
            EnumFacing ascending = grade > 0 ? curveForward : curveForward.getOpposite();
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
        for (TurnSectionTransition transition
                : findTurnSectionLayout(route, sectionFacings).transitions) {
            int i = transition.index;
            EnumFacing incoming = transition.incoming;
            EnumFacing shift = transition.shift;
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

    private static IBlockState catenaryState(List<BlockPos> route, int index,
                                             EnumFacing curveFacing, boolean support)
            throws TunnelBuildException {
        String id;
        if (support) {
            id = "nebulaecraft:catenary_steel_support";
        } else if (isSlopeRailCell(route, index)) {
            id = "nebulaecraft:catenary_steel_slope";
            curveFacing = gradeDirection(route, index) > 0
                    ? curveFacing : curveFacing.getOpposite();
        } else if (turn(route, index) != 0) {
            id = "nebulaecraft:catenary_steel_diagonal";
            // A diagonal model must retain its two actual graph connections. The curve tangent
            // chooses the axis for straight/slope wire; the local corner selects this quadrant.
            curveFacing = cornerFacing(route, index);
        } else {
            id = "nebulaecraft:catenary_steel";
        }
        Block block = resolveBlock(id + "@0");
        return nebulaFacingState(block, curveFacing);
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

    private static EnumFacing dominantFacing(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? EnumFacing.WEST : EnumFacing.EAST;
        }
        return dz < 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
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

    private static final class TrackbedLayout {
        final Set<Long> centerCells;
        final Map<Long, Boolean> sideCells;

        TrackbedLayout(Set<Long> centerCells, Map<Long, Boolean> sideCells) {
            this.centerCells = centerCells;
            this.sideCells = sideCells;
        }

        boolean contains(BlockPos pos) {
            long packed = pos.toLong();
            return centerCells.contains(packed) || sideCells.containsKey(packed);
        }
    }

    private static final class SideTrackbedBridge {
        final int firstComponent;
        final int secondComponent;
        final BlockPos pos;
        final boolean solid;

        SideTrackbedBridge(int firstComponent, int secondComponent,
                           BlockPos pos, boolean solid) {
            this.firstComponent = firstComponent;
            this.secondComponent = secondComponent;
            this.pos = pos;
            this.solid = solid;
        }
    }

    private static final class TurnPlatformAdjustment {
        final List<BlockPos> removed;
        final BlockPos added;

        TurnPlatformAdjustment(List<BlockPos> removed, BlockPos added) {
            this.removed = removed;
            this.added = added;
        }
    }

    private static final class TurnSectionTransition {
        final int index;
        final BlockPos before;
        final BlockPos oldCorner;
        final BlockPos newCorner;
        final BlockPos after;
        final BlockPos projectedOldCenter;
        final EnumFacing incoming;
        final EnumFacing shift;

        TurnSectionTransition(int index, BlockPos before, BlockPos oldCorner,
                              BlockPos newCorner, BlockPos after,
                              BlockPos projectedOldCenter,
                              EnumFacing incoming, EnumFacing shift) {
            this.index = index;
            this.before = before;
            this.oldCorner = oldCorner;
            this.newCorner = newCorner;
            this.after = after;
            this.projectedOldCenter = projectedOldCenter;
            this.incoming = incoming;
            this.shift = shift;
        }
    }

    private static final class TurnSectionLayout {
        final List<TurnSectionTransition> transitions;
        final List<TurnSectionTransition> allTransitions;

        TurnSectionLayout(List<TurnSectionTransition> transitions,
                          List<TurnSectionTransition> allTransitions) {
            this.transitions = transitions;
            this.allTransitions = allTransitions;
        }
    }

    private static final class RasterizedRoute {
        final List<BlockPos> positions;
        final List<Double> parameters;

        RasterizedRoute(List<BlockPos> positions, List<Double> parameters) {
            this.positions = positions;
            this.parameters = parameters;
        }
    }

    private static final class Geometry {
        final List<BlockPos> route;
        final List<EnumFacing> sectionFacings;
        final double length;
        final double minimumRadius;
        final double maximumGrade;

        Geometry(List<BlockPos> route, List<EnumFacing> sectionFacings,
                 double length, double minimumRadius, double maximumGrade) {
            this.route = route;
            this.sectionFacings = sectionFacings;
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
