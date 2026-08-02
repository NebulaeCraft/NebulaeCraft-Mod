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
import java.util.Arrays;
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
        GradeLayout gradeLayout = new GradeLayout(route, geometry.sectionTangents);
        Set<Integer> catenarySupportIndices = normalizedPower.equals("catenary")
                ? catenarySupportIndices(route, settings.catenarySupportSpacing)
                : Collections.<Integer>emptySet();
        ThirdRailLayout thirdRailLayout = normalizedPower.startsWith("thirdrail_")
                ? createThirdRailLayout(route, sectionFacings, gradeLayout, mirrored,
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
            for (int lateral = -shellLateral; lateral <= shellLateral; lateral++) {
                BlockPos floor = gradedOffset(center, nx, nz, lateral, gradeLayout);
                boolean raisedSectionCell = gradeLayout.isSlopeSection(floor);
                put(states, floor, concrete, 10);
                put(states, floor.up(roofY + (raisedSectionCell ? 1 : 0)), concrete, 10);
            }

            // A slope rail rises through the upper half of this slice. Its special eight-layer
            // section replaces the normal upper full-block wall with a third vertical-slab row.
            EnumFacing leftOutward = lateralFacing(nx, nz, -1);
            EnumFacing rightOutward = lateralFacing(nx, nz, 1);
            IBlockState leftVerticalSlab = sideMountedState(verticalSlab.getBlock(), leftOutward);
            IBlockState rightVerticalSlab = sideMountedState(verticalSlab.getBlock(), rightOutward);
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockPos wallFloor = gradedOffset(
                        center, nx, nz, sign * shellLateral, gradeLayout);
                boolean raisedWall = gradeLayout.isSlopeSection(wallFloor);
                IBlockState wallSlab = sign < 0 ? leftVerticalSlab : rightVerticalSlab;
                put(states, wallFloor.up(), concrete, 10);
                if (!raisedWall) {
                    put(states, wallFloor.up(preset.clearHeight), concrete, 10);
                }
                int verticalSlabTopY = raisedWall
                        ? preset.clearHeight : preset.clearHeight - 1;
                for (int y = 2; y <= verticalSlabTopY; y++) {
                    put(states, wallFloor.up(y), wallSlab, 11);
                }
            }

            for (int lateral = -shellLateral; lateral <= shellLateral; lateral++) {
                BlockPos floor = gradedOffset(center, nx, nz, lateral, gradeLayout);
                boolean raisedSectionCell = gradeLayout.isSlopeSection(floor);
                int absoluteLateral = Math.abs(lateral);
                if (raisedSectionCell) {
                    if (absoluteLateral == shellLateral) {
                        put(states, floor.up(catenaryY), concrete, 10);
                    }
                    if (absoluteLateral == shellLateral
                            || absoluteLateral == halfClearWidth) {
                        put(states, floor.up(roofY), concrete, 10);
                    } else if (absoluteLateral == 1) {
                        put(states, floor.up(roofY), horizontalSlab, 11);
                    }
                } else if (absoluteLateral == shellLateral
                        || absoluteLateral == halfClearWidth) {
                    put(states, floor.up(catenaryY), concrete, 10);
                } else if (absoluteLateral == 1) {
                    put(states, floor.up(catenaryY), horizontalSlab, 11);
                }
            }

            for (int lateral = -halfClearWidth; lateral <= halfClearWidth; lateral++) {
                BlockPos clearFloor = gradedOffset(center, nx, nz, lateral, gradeLayout);
                boolean raisedSectionCell = gradeLayout.isSlopeSection(clearFloor);
                int clearTopY = raisedSectionCell ? catenaryY : preset.clearHeight;
                for (int y = 1; y <= clearTopY; y++) {
                    put(states, clearFloor.up(y), Blocks.AIR.getDefaultState(), 20);
                }
            }
            put(states, offset(center, nx, nz, 0, slopeSection ? roofY : catenaryY),
                    Blocks.AIR.getDefaultState(), 20);

            // Normal trackbed is 44:0, 43:8, 44:0. The slope rail needs a solid three-block
            // 43:8 base so the rising Railcraft model is supported across the whole section.
            put(states, center, centerBed, 30);
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockPos sideFloor = gradedOffset(center, nx, nz, sign, gradeLayout);
                put(states, sideFloor,
                        gradeLayout.isSlopeSection(sideFloor) ? centerBed : sideBed, 30);
            }
            IBlockState railState = reinforcedTrack.getStateFromMeta(railMeta(route, i));
            put(states, center.up(), railState, 50);
            // Railcraft recalculates a flex track's shape in onBlockAdded. During the first pass
            // the next route cell does not exist yet, so a requested corner can be changed into
            // a straight rail. Reapply the explicit route shape after every track is present.
            railFinalizations.add(new TunnelPlan.Operation(center.up(), railState, false));

            // The optional colored maintenance platform sits on the intact left floor block.
            put(states, gradedOffset(center, nx, nz, -2, gradeLayout).up(), platformState, 40);

            if (normalizedPower.equals("catenary")) {
                IBlockState catenary = catenaryState(route, i, forward,
                        catenarySupportIndices.contains(i));
                put(states, center.up(catenaryY), catenary, 45);
            }

        }

        if (thirdRailLayout != null) {
            boolean yellow = normalizedPower.endsWith("yellow");
            for (ThirdRailPlacement placement : thirdRailLayout.placements) {
                int routeIndex = placement.routeIndex;
                EnumFacing forward = sectionFacings.get(routeIndex);
                int nx = -forward.getFrontOffsetZ();
                int nz = forward.getFrontOffsetX();
                if (mirrored) {
                    nx = -nx;
                    nz = -nz;
                }
                EnumFacing towardTrack = lateralFacing(nx, nz, -1);
                IBlockState thirdRail = thirdRailState(yellow, route, routeIndex, forward,
                        towardTrack, placement.slope, placement.diagonalType,
                        placement.diagonalFacing, placement.support);
                put(states, placement.pos.up(), thirdRail, 45);
            }
        }

        Set<Long> clearFootprint = applyTurnSectionTransitions(
                states, route, sectionFacings, concrete, horizontalSlab,
                verticalSlab, sideBed, centerBed, platformState, preset.clearHeight,
                preset.catenaryRecessHeight, gradeLayout, mirrored);
        orientVerticalSlabsByCurveTangent(
                states, route, sectionFacings, verticalSlab.getBlock());
        placeTunnelLights(states, route, sectionFacings, clearFootprint,
                tunnelLight, settings.lightSpacing, mirrored, gradeLayout);

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
            int recessHeight, GradeLayout gradeLayout, boolean mirrored)
            throws TunnelBuildException {
        TurnSectionLayout transitionLayout = findTurnSectionLayout(route, sectionFacings);
        List<TurnSectionTransition> transitions = transitionLayout.transitions;
        // Full 3x8 structures remain de-duplicated, but the skipped perpendicular transition still
        // owns real 43/44 floor cells. Merge every candidate into the semantic bed before clearance
        // and wall extraction so reversing the route cannot leave the new-axis bed under a wall.
        TrackbedLayout trackbedLayout = createTrackbedLayout(
                route, transitionLayout.allTransitions, gradeLayout);
        Set<Long> clearFootprint = levelRouteClearFootprint(
                route, sectionFacings, gradeLayout);
        for (TurnSectionTransition transition : transitions) {
            addTurnSectionClearFootprint(
                    clearFootprint, transition.before, transition.shift, gradeLayout);
            addTurnSectionClearFootprint(
                    clearFootprint, transition.oldCorner, transition.shift, gradeLayout);
            addTurnSectionClearFootprint(clearFootprint,
                    transition.projectedOldCenter, transition.shift, gradeLayout);
        }
        mergeSlopeClearanceFootprint(clearFootprint, gradeLayout);
        addTrackbedClearanceFootprint(
                clearFootprint, trackbedLayout, route, gradeLayout);

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
                trackbedLayout, clearFootprint, platformState,
                gradeLayout, mirrored);

        Set<Long> wallBoundary = rebuildTurnShell(
                states, route, sectionFacings, clearFootprint,
                concrete, verticalSlab, clearHeight, recessHeight, gradeLayout);
        reinforceSlopeSections(states, gradeLayout, concrete, horizontalSlab,
                clearHeight, recessHeight);
        addTurnWallCornerBackings(states, route, clearFootprint, wallBoundary,
                concrete, clearHeight, recessHeight);
        restoreTurnCatenaryRecess(states, transitionLayout.highGradeRecessTransitions,
                horizontalSlab, clearHeight);
        sealCorrectedSlopeWallCaps(states, gradeLayout, wallBoundary,
                concrete, clearHeight + 1);
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
        List<TurnSectionTransition> highGradeRecessTransitions = new ArrayList<>();
        for (int i = 1; i + 2 < route.size(); i++) {
            BlockPos before = route.get(i - 1);
            BlockPos oldCorner = route.get(i);
            BlockPos newCorner = route.get(i + 1);
            BlockPos after = route.get(i + 2);
            // A slope may now meet the low-side outer edge of this transition. Keep the transverse
            // pair level and allow exactly one of the two outer edges to change by one block; the
            // three template rows will then be written at their own high/low floor levels.
            boolean incomingGrade = before.getY() != oldCorner.getY();
            boolean outgoingGrade = newCorner.getY() != after.getY();
            if (oldCorner.getY() != newCorner.getY()
                    || incomingGrade && outgoingGrade
                    || incomingGrade && Math.abs(before.getY() - oldCorner.getY()) != 1
                    || outgoingGrade && Math.abs(newCorner.getY() - after.getY()) != 1) {
                continue;
            }

            EnumFacing incoming = horizontalDirection(before, oldCorner);
            EnumFacing shift = horizontalDirection(oldCorner, newCorner);
            EnumFacing outgoing = horizontalDirection(newCorner, after);
            if (incoming == null || shift == null || outgoing != incoming
                    || shift.getAxis() == incoming.getAxis()) {
                continue;
            }

            TurnSectionTransition candidate = new TurnSectionTransition(i, before, oldCorner,
                    newCorner, after, after.offset(shift.getOpposite()), incoming, shift);
            // The level turn immediately on the high side of a grade shares its recess ceiling
            // with the raised low-side slope shell. It may be perpendicular to the dominant smooth
            // section axis and therefore excluded from full 3x8 structures, but its explicit
            // slab/air recess row still owns that overlap.
            boolean levelTransition = before.getY() == oldCorner.getY()
                    && oldCorner.getY() == newCorner.getY()
                    && newCorner.getY() == after.getY();
            boolean lowerBefore = i >= 2
                    && route.get(i - 2).getY() < before.getY();
            boolean lowerAfter = i + 3 < route.size()
                    && route.get(i + 3).getY() < after.getY();
            if (levelTransition && (lowerBefore || lowerAfter)) {
                highGradeRecessTransitions.add(candidate);
            }
            if (sectionFacings.get(i).getAxis() != incoming.getAxis()) {
                continue;
            }
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
        return new TurnSectionLayout(
                transitions, allTransitions, highGradeRecessTransitions);
    }

    private static Set<Long> levelRouteClearFootprint(
            List<BlockPos> route, List<EnumFacing> sectionFacings,
            GradeLayout gradeLayout) {
        Set<Long> footprint = new HashSet<>();
        for (int i = 0; i < route.size(); i++) {
            EnumFacing lateral = sectionFacings.get(i).rotateY();
            for (int offset = -2; offset <= 2; offset++) {
                footprint.add(gradeLayout.floorAt(
                        route.get(i).offset(lateral, offset)).toLong());
            }
        }
        return footprint;
    }

    private static void addTurnSectionClearFootprint(Set<Long> clearFootprint,
                                                     BlockPos center, EnumFacing shift,
                                                     GradeLayout gradeLayout) {
        for (int offset = -2; offset <= 3; offset++) {
            clearFootprint.add(gradeLayout.floorAt(
                    center.offset(shift, offset)).toLong());
        }
    }

    /**
     * Make the five clear cells of every analytic slope section authoritative at the low floor.
     * Rounding a near-diagonal normal can otherwise make one of these cells belong to the adjacent
     * high section, so its floor is left one block too high even though the dedicated slope shell
     * later clears and roofs the low column.
     */
    private static void mergeSlopeClearanceFootprint(
            Set<Long> clearFootprint, GradeLayout gradeLayout) {
        for (GradeTransition transition : gradeLayout.transitions) {
            for (int lateral = -2; lateral <= 2; lateral++) {
                BlockPos floor = transition.normalOffset(lateral);
                clearFootprint.remove(floor.up().toLong());
                clearFootprint.add(floor.toLong());
            }
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
                                                       List<BlockPos> route,
                                                       GradeLayout gradeLayout) {
        Set<Long> trackbedCells = new HashSet<>(trackbedLayout.centerCells);
        trackbedCells.addAll(trackbedLayout.sideCells.keySet());
        for (long packed : trackbedCells) {
            BlockPos trackbed = BlockPos.fromLong(packed);
            clearFootprint.add(packed);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos floor = gradeLayout.floorAt(trackbed.offset(direction));
                // At a one-block grade change, the next slice's bed occupies this XZ column one
                // level higher or lower. Dilating either bed into the other slice would make the
                // rebuilt floor place concrete above the low 43:8 row and below the high row.
                // Level curves still receive the full cardinal dilation used by their widened
                // transition templates.
                if (!trackbedCells.contains(floor.up().toLong())
                        && !trackbedCells.contains(floor.down().toLong())
                        && !isBeyondOpenRouteEnd(floor, route)) {
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
                                                int clearHeight, int recessHeight,
                                                GradeLayout gradeLayout)
            throws TunnelBuildException {
        int roofY = clearHeight + 1 + recessHeight;
        Set<Long> boundary = new HashSet<>();
        for (long packed : clearFootprint) {
            BlockPos clear = BlockPos.fromLong(packed);
            // Floor, walls, and ceiling must all come from the same final semantic clearance.
            // A de-duplicated axis-switch template still contributes trackbed clearance; relying
            // only on retained full templates for the two ceiling layers leaves that clearance
            // uncapped. These defaults sit below every explicit recess air/slab/wire state.
            int slopeLift = gradeLayout.isSlopeSection(clear) ? 1 : 0;
            put(states, clear.up(clearHeight + 1 + slopeLift), concrete, 9);
            put(states, clear.up(roofY + slopeLift), concrete, 9);

            // The entire horizontal opening is authoritative. Clear remnants left by differently
            // oriented normal sections before reconstructing its outer boundary.
            for (int y = 1; y <= clearHeight + slopeLift; y++) {
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

        Set<Long> raisedSlopeWalls = gradeLayout.raisedSlopeWallColumns(boundary);
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
            boolean slopeTop = raisedSlopeWalls.contains(packed);
            int wallRoofY = roofY + (slopeTop ? 1 : 0);
            for (int y = 0; y <= wallRoofY; y++) {
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
            // A neighbouring slice can initially classify this X/Z one floor higher and leave its
            // low-priority roof above the finalized wall cap. Delete that obsolete plan entry;
            // writing AIR here would unnecessarily replace whatever the world originally contains.
            removePlannedState(states, wall.up(wallRoofY + 1), concrete);
        }
        return boundary;
    }

    /**
     * When integer rasterization assigns one edge of the five-cell slope section to the adjacent
     * high slice, its raised wall row has to move toward the grade midpoint. The vacated wall's
     * inward ceiling corner is then concrete rather than slope clearance; sealing it here keeps the
     * shifted wall tied into the ordinary high-side roof.
     */
    private static void sealCorrectedSlopeWallCaps(
            Map<Long, PlannedState> states, GradeLayout gradeLayout,
            Set<Long> wallBoundary, IBlockState concrete, int catenaryY) {
        for (BlockPos cap : gradeLayout.correctedSlopeWallCaps(
                wallBoundary, catenaryY)) {
            put(states, cap, concrete, 41);
        }
    }

    /**
     * Reapply the five-cell interior of the eight-layer slope section on the rasterized analytic
     * normal. The ordinary section loop uses a cardinal dominant axis, while the slope wire and
     * raised recess must follow the true curve normal. Walls and their diagonal backings remain the
     * responsibility of the final clearance boundary.
     */
    private static void reinforceSlopeSections(
            Map<Long, PlannedState> states, GradeLayout gradeLayout,
            IBlockState concrete, IBlockState horizontalSlab,
            int clearHeight, int recessHeight)
            throws TunnelBuildException {
        int catenaryY = clearHeight + 1;
        int roofY = catenaryY + recessHeight;
        for (GradeTransition transition : gradeLayout.transitions) {
            // The cardinal boundary derived from the final clearance owns both wall faces and
            // diagonal concrete backings. Replaying the two +/-3 endpoints here changes a backing
            // into a vertical slab near a 45-degree tangent and can raise an isolated roof block.
            for (int lateral = -2; lateral <= 2; lateral++) {
                BlockPos floor = transition.normalOffset(lateral);
                int absoluteLateral = Math.abs(lateral);
                for (int y = 1; y <= catenaryY; y++) {
                    put(states, floor.up(y), Blocks.AIR.getDefaultState(), 39);
                }
                if (absoluteLateral == 2) {
                    put(states, floor.up(roofY), concrete, 39);
                } else if (absoluteLateral == 1) {
                    put(states, floor.up(roofY), horizontalSlab, 39);
                } else {
                    put(states, floor.up(roofY), Blocks.AIR.getDefaultState(), 39);
                }
                put(states, floor.up(roofY + 1), concrete, 39);
            }
        }
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
            boolean raisedBacking = false;
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos adjacent = corner.offset(direction);
                PlannedState adjacentTop = states.get(
                        adjacent.up(roofY + 1).toLong());
                if (!backings.contains(adjacent.toLong())
                        && adjacentTop != null
                        && adjacentTop.state.equals(concrete)) {
                    raisedBacking = true;
                    break;
                }
            }
            int backingRoofY = roofY + (raisedBacking ? 1 : 0);
            for (int y = 0; y <= backingRoofY; y++) {
                put(states, corner.up(y), concrete, 38);
            }
            if (!raisedBacking) {
                removePlannedState(states, corner.up(roofY + 1), concrete);
            }
        }
    }

    /** Remove a lower-priority structural default that lies outside the finalized shell. */
    private static void removePlannedState(Map<Long, PlannedState> states,
                                           BlockPos pos, IBlockState state) {
        PlannedState planned = states.get(pos.toLong());
        if (planned != null && planned.priority < 38 && planned.state.equals(state)) {
            states.remove(pos.toLong());
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
            List<BlockPos> route, List<TurnSectionTransition> transitions,
            GradeLayout gradeLayout) {
        Set<Long> routeCells = new HashSet<>();
        Set<Long> centerCells = new HashSet<>();
        Map<Long, Boolean> sideCells = new LinkedHashMap<>();
        for (BlockPos center : route) {
            routeCells.add(center.toLong());
            centerCells.add(center.toLong());
        }
        for (int i = 0; i < route.size(); i++) {
            BlockPos center = route.get(i);
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
                BlockPos left = gradeLayout.floorAt(
                        center.offset(connection.rotateYCCW()));
                BlockPos right = gradeLayout.floorAt(
                        center.offset(connection.rotateY()));
                if (!routeCells.contains(left.toLong())) {
                    sideCells.put(left.toLong(),
                            gradeLayout.isSlopeSection(left)
                                    || Boolean.TRUE.equals(sideCells.get(left.toLong())));
                }
                if (!routeCells.contains(right.toLong())) {
                    sideCells.put(right.toLong(),
                            gradeLayout.isSlopeSection(right)
                                    || Boolean.TRUE.equals(sideCells.get(right.toLong())));
                }
            }
        }

        for (TurnSectionTransition transition : transitions) {
            addTurnTrackbedLayoutRow(sideCells, centerCells,
                    transition.before, transition.shift,
                    new int[]{-1, 1, 2}, new int[]{0}, gradeLayout);
            addTurnTrackbedLayoutRow(sideCells, centerCells,
                    transition.oldCorner, transition.shift,
                    new int[]{-1, 2}, new int[]{0, 1}, gradeLayout);
            addTurnTrackbedLayoutRow(sideCells, centerCells,
                    transition.projectedOldCenter, transition.shift,
                    new int[]{-1, 0, 2}, new int[]{1}, gradeLayout);
        }
        mergeSlopeTrackbedSections(centerCells, sideCells, gradeLayout);
        for (long packed : centerCells) {
            sideCells.remove(packed);
        }
        connectSideTrackbedCardinally(sideCells, centerCells, route);
        return new TrackbedLayout(centerCells, sideCells);
    }

    /**
     * The slope rail base is the analytic normal's three-cell 43:8 row. Remove any side-bed cell
     * inherited one level above that row from a neighbouring high slice before installing it;
     * otherwise the upper slab wins after the slope clearance pass and hides the low inner block.
     */
    private static void mergeSlopeTrackbedSections(
            Set<Long> centerCells, Map<Long, Boolean> sideCells,
            GradeLayout gradeLayout) {
        for (GradeTransition transition : gradeLayout.transitions) {
            for (int lateral = -2; lateral <= 2; lateral++) {
                BlockPos floor = transition.normalOffset(lateral);
                centerCells.remove(floor.up().toLong());
                sideCells.remove(floor.up().toLong());
                if (Math.abs(lateral) <= 1) {
                    centerCells.add(floor.toLong());
                }
            }
        }
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
                            || centerCells.contains(bridge.up().toLong())
                            || centerCells.contains(bridge.down().toLong())
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
                                                  int[] sideOffsets, int[] centerOffsets,
                                                  GradeLayout gradeLayout) {
        for (int offset : sideOffsets) {
            BlockPos side = gradeLayout.floorAt(center.offset(shift, offset));
            sideCells.put(side.toLong(), gradeLayout.isSlopeSection(side));
        }
        for (int offset : centerOffsets) {
            centerCells.add(gradeLayout.floorAt(
                    center.offset(shift, offset)).toLong());
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
                                            GradeLayout gradeLayout,
                                            boolean mirrored)
            throws TunnelBuildException {
        List<TurnPlatformAdjustment> adjustments = new ArrayList<>();
        for (TurnSectionTransition transition : transitions) {
            TurnPlatformAdjustment adjustment = turnPlatformAdjustment(
                    transition, gradeLayout, mirrored);
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
            TurnSectionTransition transition, GradeLayout gradeLayout,
            boolean mirrored) {
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
            removed.add(platformPosition(
                    before, incoming, mirrored, gradeLayout));
            removed.add(platformPosition(
                    oldCorner, incoming, mirrored, gradeLayout));
            added = platformPosition(
                    before.offset(shift), incoming, mirrored, gradeLayout);
        } else {
            removed.add(platformPosition(
                    newCorner, incoming, mirrored, gradeLayout));
            removed.add(platformPosition(
                    after, incoming, mirrored, gradeLayout));
            added = platformPosition(
                    after.offset(shift.getOpposite()), incoming, mirrored, gradeLayout);
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

    /**
     * Place a transition platform anchor on the grade plane belonging to its own horizontal cell.
     * A low-side curve anchor can project across the analytic grade boundary even when the route
     * center used to derive it is one block lower; inheriting the center Y leaves that anchor below
     * the final clearance and makes the bridge search report a false trackbed obstruction.
     */
    private static BlockPos platformPosition(
            BlockPos center, EnumFacing forward, boolean mirrored,
            GradeLayout gradeLayout) {
        EnumFacing platformSide = mirrored ? forward.rotateY() : forward.rotateYCCW();
        return gradeLayout.floorAt(center.offset(platformSide, 2)).up();
    }

    /** Place each light in the last clear cell before the final platform-side wall. */
    private static void placeTunnelLights(Map<Long, PlannedState> states,
                                          List<BlockPos> route,
                                          List<EnumFacing> sectionFacings,
                                          Set<Long> clearFootprint,
                                          Block tunnelLight,
                                          int spacing,
                                          boolean mirrored,
                                          GradeLayout gradeLayout)
            throws TunnelBuildException {
        int safeSpacing = Math.max(1, spacing);
        for (int i = 0; i < route.size(); i += safeSpacing) {
            EnumFacing forward = sectionFacings.get(i);
            EnumFacing outward = mirrored ? forward.rotateY() : forward.rotateYCCW();
            BlockPos lightFloor = gradeLayout.floorAt(route.get(i));
            BlockPos next = gradeLayout.floorAt(lightFloor.offset(outward));
            while (clearFootprint.contains(next.toLong())) {
                lightFloor = next;
                next = gradeLayout.floorAt(lightFloor.offset(outward));
            }
            IBlockState lightState = sideMountedState(tunnelLight, outward);
            int lightY = gradeLayout.isSlopeSection(lightFloor) ? 5 : 4;
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

    /**
     * Reapply only the explicit recess semantics after slope and wall reconstruction. A turn row
     * beside a one-block grade change can share its ceiling coordinate with the slope shell; the
     * subtype1 slab or central opening belongs to the widened turn and must win that final merge.
     * Concrete defaults are intentionally not replayed, so this pass cannot create exterior blocks.
     */
    private static void restoreTurnCatenaryRecess(
            Map<Long, PlannedState> states,
            List<TurnSectionTransition> transitions,
            IBlockState horizontalSlab, int clearHeight) {
        for (TurnSectionTransition transition : transitions) {
            restoreTurnCatenaryRow(states, transition.before, transition.shift,
                    horizontalSlab, clearHeight,
                    new int[]{-1, 1, 2}, new int[]{0});
            restoreTurnCatenaryRow(states, transition.oldCorner, transition.shift,
                    horizontalSlab, clearHeight,
                    new int[]{-1, 2}, new int[]{0, 1});
            restoreTurnCatenaryRow(states, transition.projectedOldCenter, transition.shift,
                    horizontalSlab, clearHeight,
                    new int[]{-1, 0, 2}, new int[]{1});
        }
    }

    private static void restoreTurnCatenaryRow(
            Map<Long, PlannedState> states, BlockPos center, EnumFacing shift,
            IBlockState horizontalSlab, int clearHeight,
            int[] sideOffsets, int[] centerOffsets) {
        int catenaryY = clearHeight + 1;
        for (int offset : sideOffsets) {
            put(states, center.offset(shift, offset).up(catenaryY),
                    horizontalSlab, 41);
        }
        for (int offset : centerOffsets) {
            put(states, center.offset(shift, offset).up(catenaryY),
                    Blocks.AIR.getDefaultState(), 42);
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
        List<Vec> sectionTangents = curveTangents(
                rasterized.parameters, p0, p1, v0, v1);
        List<EnumFacing> sectionFacings = curveTangentFacings(sectionTangents, v0);
        List<BlockPos> gradedRoute = alignGradesToCurveSections(
                rasterized.positions, rasterized.parameters, sectionFacings,
                p0, p1, v0, v1, preset.maximumGrade);
        return new Geometry(gradedRoute, sectionFacings, sectionTangents,
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

    private static List<Vec> curveTangents(
            List<Double> parameters, Vec p0, Vec p1, Vec v0, Vec v1) {
        List<Vec> tangents = new ArrayList<>(parameters.size());
        for (double parameter : parameters) {
            tangents.add(quinticTangent(p0, p1, v0, v1, parameter));
        }
        return tangents;
    }

    private static List<EnumFacing> curveTangentFacings(
            List<Vec> tangents, Vec initialTangent) {
        List<EnumFacing> facings = new ArrayList<>(tangents.size());
        EnumFacing previous = dominantFacing(initialTangent.x, initialTangent.z);
        for (Vec tangent : tangents) {
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
            while (current.getX() != targetX || current.getZ() != targetZ) {
                int sx = Integer.signum(targetX - current.getX());
                int sz = Integer.signum(targetZ - current.getZ());
                boolean moveX = sx != 0 && (sz == 0 || Math.abs(point.x - current.getX()) >= Math.abs(point.z - current.getZ()));
                int nextX = current.getX() + (moveX ? sx : 0);
                int nextZ = current.getZ() + (moveX ? 0 : sz);
                BlockPos next = new BlockPos(nextX, start.getY(), nextZ);
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
        return new RasterizedRoute(result, parameters);
    }

    /**
     * Snap every one-block grade change to a complete tangent-normal section. A rasterized lateral
     * shift consists of incoming, transverse, and outgoing edges; changing Y on any of those three
     * edges splits the widened 3x8 curve template between two levels. Match all thresholds to
     * tangent-aligned edges globally so the relocated steps remain separated and no ascending rail
     * is combined with a horizontal curve corner.
     */
    private static List<BlockPos> alignGradesToCurveSections(
            List<BlockPos> horizontalRoute, List<Double> parameters,
            List<EnumFacing> sectionFacings,
            Vec p0, Vec p1, Vec v0, Vec v1,
            double maximumGrade) throws TunnelBuildException {
        int startY = (int) Math.round(p0.y);
        int endY = (int) Math.round(p1.y);
        if (startY == endY) {
            return horizontalRoute;
        }
        int direction = Integer.signum(endY - startY);
        int transitionCount = Math.abs(endY - startY);
        Set<Integer> forbiddenEdges = turnTransitionEdges(
                horizontalRoute, sectionFacings, direction);
        boolean[] eligible = new boolean[horizontalRoute.size()];
        for (int i = 1; i < horizontalRoute.size(); i++) {
            EnumFacing edge = horizontalDirection(
                    horizontalRoute.get(i - 1), horizontalRoute.get(i));
            eligible[i] = edge != null
                    && edge.getAxis() == sectionFacings.get(i).getAxis()
                    && isStraightSlopeEdge(horizontalRoute, i, direction)
                    && !forbiddenEdges.contains(i);
        }

        List<Integer> desiredEdges = new ArrayList<>(transitionCount);
        for (int step = 1; step <= transitionCount; step++) {
            double threshold = startY + direction * (step - 0.5);
            int desired = -1;
            for (int i = 1; i < parameters.size(); i++) {
                double curveY = quintic(p0, p1, v0, v1, parameters.get(i)).y;
                if (direction > 0 ? curveY >= threshold : curveY <= threshold) {
                    desired = i;
                    break;
                }
            }
            if (desired < 0) {
                desired = horizontalRoute.size() - 1;
            }
            desiredEdges.add(desired);
        }

        int minimumSlopeSpacing = Math.max(1,
                (int) Math.ceil(1.0 / maximumGrade - 1.0E-9));
        List<Integer> selectedEdges = selectGradeEdges(
                eligible, desiredEdges, minimumSlopeSpacing, direction);

        List<BlockPos> result = new ArrayList<>(horizontalRoute.size());
        int applied = 0;
        for (int i = 0; i < horizontalRoute.size(); i++) {
            while (applied < selectedEdges.size() && selectedEdges.get(applied) <= i) {
                applied++;
            }
            BlockPos horizontal = horizontalRoute.get(i);
            result.add(new BlockPos(horizontal.getX(), startY + direction * applied,
                    horizontal.getZ()));
        }
        if (result.get(result.size() - 1).getY() != endY) {
            throw new TunnelBuildException("坡度无法转换为连续的轨道台阶");
        }
        return result;
    }

    /**
     * Match all analytic height thresholds to usable rail edges at once. A greedy snap can move
     * one threshold across a long run of overlapping curve templates and leave the following
     * threshold on the first edge after it, clustering two slopes even though usable space exists
     * later. Dynamic programming keeps every pair at least one configured grade run apart while
     * minimizing the total displacement from the smooth height curve.
     */
    private static List<Integer> selectGradeEdges(
            boolean[] eligible, List<Integer> desiredEdges,
            int minimumSpacing, int direction) throws TunnelBuildException {
        final long unreachable = Long.MAX_VALUE / 4;
        int stepCount = desiredEdges.size();
        int edgeCount = eligible.length;
        long[] previousCosts = new long[edgeCount];
        Arrays.fill(previousCosts, unreachable);
        int[][] parents = new int[stepCount][edgeCount];
        for (int[] row : parents) {
            Arrays.fill(row, -1);
        }
        for (int edge = 1; edge < edgeCount; edge++) {
            if (eligible[edge]) {
                previousCosts[edge] = gradeEdgeCost(
                        edge, desiredEdges.get(0), direction);
            }
        }

        for (int step = 1; step < stepCount; step++) {
            long[] currentCosts = new long[edgeCount];
            Arrays.fill(currentCosts, unreachable);
            long bestPreviousCost = unreachable;
            int bestPreviousEdge = -1;
            for (int edge = 1; edge < edgeCount; edge++) {
                int predecessor = edge - minimumSpacing;
                if (predecessor >= 1
                        && previousCosts[predecessor] < bestPreviousCost) {
                    bestPreviousCost = previousCosts[predecessor];
                    bestPreviousEdge = predecessor;
                }
                if (eligible[edge] && bestPreviousEdge >= 0) {
                    currentCosts[edge] = bestPreviousCost + gradeEdgeCost(
                            edge, desiredEdges.get(step), direction);
                    parents[step][edge] = bestPreviousEdge;
                }
            }
            previousCosts = currentCosts;
        }

        int selected = -1;
        long selectedCost = unreachable;
        for (int edge = 1; edge < edgeCount; edge++) {
            if (previousCosts[edge] < selectedCost) {
                selected = edge;
                selectedCost = previousCosts[edge];
            }
        }
        if (selected < 0) {
            throw new TunnelBuildException(String.format(Locale.ROOT,
                    "坡度附近没有足够间距的完整切线断面（至少 %d 格）",
                    minimumSpacing));
        }

        List<Integer> selectedEdges = new ArrayList<>(
                Collections.nCopies(stepCount, 0));
        for (int step = stepCount - 1; step >= 0; step--) {
            selectedEdges.set(step, selected);
            if (step > 0) {
                selected = parents[step][selected];
            }
        }
        return selectedEdges;
    }

    private static long gradeEdgeCost(int edge, int desired, int direction) {
        long distance = Math.abs((long) edge - desired);
        boolean onPreferredLowSide = direction > 0
                ? edge <= desired : edge >= desired;
        return distance * distance * 2 + (onPreferredLowSide ? 0 : 1);
    }

    /**
     * Keep a grade off the transverse edge and the high-side boundary of a widened curve template.
     * The low-side outer edge is safe when its slope rail is straight: the three template rows can
     * then follow their respective grades and meet the dedicated low-end slope section. Blocking
     * all three edges makes overlapping diagonal templates exclude very long stretches of route.
     */
    private static Set<Integer> turnTransitionEdges(
            List<BlockPos> route, List<EnumFacing> sectionFacings,
            int gradeDirection) {
        Set<Integer> result = new HashSet<>();
        for (int i = 1; i + 2 < route.size(); i++) {
            EnumFacing incoming = horizontalDirection(route.get(i - 1), route.get(i));
            EnumFacing shift = horizontalDirection(route.get(i), route.get(i + 1));
            EnumFacing outgoing = horizontalDirection(route.get(i + 1), route.get(i + 2));
            if (incoming != null && outgoing == incoming && shift != null
                    && shift.getAxis() != incoming.getAxis()
                    && sectionFacings.get(i).getAxis() == incoming.getAxis()) {
                result.add(i + 1);
                result.add(gradeDirection > 0 ? i + 2 : i);
            }
        }
        return result;
    }

    private static boolean isStraightSlopeEdge(
            List<BlockPos> route, int edgeIndex, int gradeDirection) {
        int lowIndex = gradeDirection > 0 ? edgeIndex - 1 : edgeIndex;
        return lowIndex > 0 && lowIndex + 1 < route.size()
                && turn(route, lowIndex) == 0;
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
                                               boolean slope,
                                               int diagonalType, EnumFacing diagonalFacing,
                                               boolean support)
            throws TunnelBuildException {
        String color = yellow ? "yellow" : "white";
        String id = "nebulaecraft:thirdrail_" + color;
        int grade = gradeDirection(route, index);
        EnumFacing modelFacing = railSide;
        if (slope) {
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
     * Build the third rail as its own ordered lane instead of assuming that every track index has
     * one usable side cell. On the inside of a rasterized curve the calibrated diagonal_1/2 pair
     * is anchored before the two track corners. On the outside the same geometry is reversed and
     * anchored after them; otherwise diagonal_2 lands directly on the centre track.
     *
     * The smooth tangent changes its dominant axis near the middle of a 90-degree curve. At that
     * point the two offset lanes either overlap two cells (inside) or leave a two-cell gap
     * (outside). Remove duplicate/track cells, bridge only the remaining local gap, then derive all
     * diagonal models from the actual neighbouring third-rail positions. This keeps both curve
     * sides, route directions, colours, and mirror mode under the same connectivity rule.
     *
     * Each adjacent diagonal_1/diagonal_2 pair consumes one effective spacing unit. If that unit
     * receives a support, only its diagonal_1 cell may use the diagonal support model.
     */
    private static ThirdRailLayout createThirdRailLayout(List<BlockPos> route,
                                                          List<EnumFacing> sectionFacings,
                                                          GradeLayout gradeLayout,
                                                          boolean mirrored, int supportSpacing)
            throws TunnelBuildException {
        int size = route.size();
        BlockPos[] candidates = new BlockPos[size];
        for (int i = 0; i < size; i++) {
            EnumFacing placementSide = mirrored
                    ? sectionFacings.get(i).rotateYCCW()
                    : sectionFacings.get(i).rotateY();
            candidates[i] = gradeLayout.floorAt(
                    route.get(i).offset(placementSide));
        }

        // Re-anchor each selected one-block shift. The old formula is the inside case only. Its
        // outside mirror must move the second corner forward and farther away from the centreline.
        for (TurnSectionTransition transition
                : findTurnSectionLayout(route, sectionFacings).transitions) {
            int i = transition.index;
            EnumFacing incoming = transition.incoming;
            EnumFacing shift = transition.shift;
            EnumFacing placementSide = mirrored ? incoming.rotateYCCW() : incoming.rotateY();
            if (shift == placementSide) {
                candidates[i] = candidates[i]
                        .offset(incoming.getOpposite()).offset(shift);
            } else {
                candidates[i + 1] = candidates[i + 1]
                        .offset(incoming).offset(placementSide);
            }
        }

        Set<Long> trackCells = new HashSet<>();
        for (BlockPos track : route) {
            trackCells.add(track.toLong());
        }
        Set<Long> used = new HashSet<>();
        List<ThirdRailPlacement> placements = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            BlockPos candidate = candidates[i];
            long packed = candidate.toLong();
            // An inside-axis switch creates one centre-track candidate and one duplicate. Neither
            // represents a physical third-rail length unit, so omit both before model selection.
            if (trackCells.contains(packed) || used.contains(packed)) {
                continue;
            }
            if (!placements.isEmpty()) {
                ThirdRailPlacement previous = placements.get(placements.size() - 1);
                int horizontalGap = horizontalManhattanDistance(previous.pos, candidate);
                if (previous.pos.getY() == candidate.getY() && horizontalGap > 1) {
                    List<BlockPos> bridge = findThirdRailBridge(
                            previous.pos, candidate, trackCells, used);
                    if (bridge.isEmpty()) {
                        throw new TunnelBuildException("第三轨在切线方向转换处无法连续连接");
                    }
                    for (BlockPos bridgePos : bridge) {
                        used.add(bridgePos.toLong());
                        placements.add(new ThirdRailPlacement(bridgePos, i, false));
                    }
                } else if (horizontalGap != 1
                        || Math.abs(previous.pos.getY() - candidate.getY()) > 1) {
                    throw new TunnelBuildException("第三轨方格化路径不连续");
                }
            }
            used.add(packed);
            placements.add(new ThirdRailPlacement(
                    candidate, i, gradeLayout.isSlopeSection(candidate)));
        }

        List<BlockPos> thirdRailRoute = new ArrayList<>(placements.size());
        for (ThirdRailPlacement placement : placements) {
            thirdRailRoute.add(placement.pos);
        }
        for (int i = 1; i + 1 < placements.size(); i++) {
            ThirdRailPlacement placement = placements.get(i);
            if (placement.slope) {
                continue;
            }
            int physicalTurn = turn(thirdRailRoute, i);
            if (physicalTurn == 0) {
                continue;
            }
            placement.diagonalType = physicalTurn == (mirrored ? -1 : 1) ? 1 : 2;
            EnumFacing physicalCorner = cornerFacing(thirdRailRoute, i);
            placement.diagonalFacing = placement.diagonalType == 1
                    ? physicalCorner.getOpposite() : physicalCorner;
        }

        int spacing = Math.max(1, supportSpacing);
        int effectiveIndex = 0;
        for (int i = 0; i < placements.size(); ) {
            ThirdRailPlacement placement = placements.get(i);
            if (placement.slope) {
                effectiveIndex++;
                i++;
                continue;
            }
            if (placement.diagonalType != 0 && i + 1 < placements.size()) {
                ThirdRailPlacement next = placements.get(i + 1);
                if (!next.slope && next.diagonalType != 0
                        && placement.diagonalType != next.diagonalType) {
                    if (effectiveIndex % spacing == 0) {
                        ThirdRailPlacement diagonalOne = placement.diagonalType == 1
                                ? placement : next;
                        diagonalOne.support = true;
                    }
                    effectiveIndex++;
                    i += 2;
                    continue;
                }
            }
            if (effectiveIndex % spacing == 0 && placement.diagonalType != 2) {
                placement.support = true;
            }
            effectiveIndex++;
            i++;
        }
        return new ThirdRailLayout(placements);
    }

    private static int horizontalManhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getZ() - second.getZ());
    }

    /** Return only the intermediate cells of a short, track-avoiding horizontal bridge. */
    private static List<BlockPos> findThirdRailBridge(
            BlockPos start, BlockPos end, Set<Long> trackCells, Set<Long> used) {
        int shortest = horizontalManhattanDistance(start, end);
        if (start.getY() != end.getY() || shortest <= 1
                || shortest > LOCAL_TRANSITION_PATH_LIMIT) {
            return Collections.emptyList();
        }
        int minX = Math.min(start.getX(), end.getX()) - 2;
        int maxX = Math.max(start.getX(), end.getX()) + 2;
        int minZ = Math.min(start.getZ(), end.getZ()) - 2;
        int maxZ = Math.max(start.getZ(), end.getZ()) + 2;
        int maxDepth = shortest + 4;
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Map<Long, Long> previous = new HashMap<>();
        Map<Long, Integer> depths = new HashMap<>();
        queue.add(start);
        visited.add(start.toLong());
        depths.put(start.toLong(), 0);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            int depth = depths.get(current.toLong());
            if (depth >= maxDepth) {
                continue;
            }
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos next = current.offset(direction);
                if (next.getX() < minX || next.getX() > maxX
                        || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                long packed = next.toLong();
                if (trackCells.contains(packed)
                        || used.contains(packed) && !next.equals(end)
                        || !visited.add(packed)) {
                    continue;
                }
                previous.put(packed, current.toLong());
                depths.put(packed, depth + 1);
                if (next.equals(end)) {
                    List<BlockPos> path = reconstructBoundaryPath(previous, start, end);
                    if (path.size() <= 2) {
                        return Collections.emptyList();
                    }
                    return new ArrayList<>(path.subList(1, path.size() - 1));
                }
                queue.addLast(next);
            }
        }
        return Collections.emptyList();
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

    private static BlockPos gradedOffset(BlockPos center, int nx, int nz, int lateral,
                                         GradeLayout gradeLayout) {
        return gradeLayout.floorAt(offset(center, nx, nz, lateral, 0));
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
        final List<ThirdRailPlacement> placements;

        ThirdRailLayout(List<ThirdRailPlacement> placements) {
            this.placements = placements;
        }
    }

    private static final class ThirdRailPlacement {
        final BlockPos pos;
        final int routeIndex;
        final boolean slope;
        int diagonalType;
        EnumFacing diagonalFacing;
        boolean support;

        ThirdRailPlacement(BlockPos pos, int routeIndex, boolean slope) {
            this.pos = pos;
            this.routeIndex = routeIndex;
            this.slope = slope;
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
        final List<TurnSectionTransition> highGradeRecessTransitions;

        TurnSectionLayout(List<TurnSectionTransition> transitions,
                          List<TurnSectionTransition> allTransitions,
                          List<TurnSectionTransition> highGradeRecessTransitions) {
            this.transitions = transitions;
            this.allTransitions = allTransitions;
            this.highGradeRecessTransitions = highGradeRecessTransitions;
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

    /** Assign every horizontal tunnel cell to a level using tangent-normal grade planes. */
    private static final class GradeLayout {
        final List<BlockPos> route;
        final List<Vec> tangents;
        final List<GradeTransition> transitions = new ArrayList<>();
        final Map<Long, GradeTransition> slopeInteriors = new HashMap<>();
        final Set<GradeTransition> boundaryCorrections = new HashSet<>();

        GradeLayout(List<BlockPos> route, List<Vec> tangents) {
            this.route = route;
            this.tangents = tangents;
            for (int i = 1; i < route.size(); i++) {
                int deltaY = route.get(i).getY() - route.get(i - 1).getY();
                if (deltaY == 0) {
                    continue;
                }
                int lowIndex = deltaY > 0 ? i - 1 : i;
                int highIndex = deltaY > 0 ? i : i - 1;
                transitions.add(new GradeTransition(
                        route.get(lowIndex), route.get(highIndex),
                        tangents.get(lowIndex)));
            }
            for (GradeTransition transition : transitions) {
                for (int lateral = -2; lateral <= 2; lateral++) {
                    BlockPos interior = transition.normalOffset(lateral);
                    slopeInteriors.put(xzKey(
                            interior.getX(), interior.getZ()), transition);
                }
                if (needsBoundaryCorrection(transition)) {
                    boundaryCorrections.add(transition);
                }
            }
        }

        BlockPos floorAt(BlockPos horizontal) {
            GradeTransition slope = slopeInteriorAt(horizontal);
            if (slope != null) {
                return new BlockPos(horizontal.getX(), slope.lowCenter.getY(),
                        horizontal.getZ());
            }
            int grade = route.get(sectionIndex(horizontal)).getY();
            return new BlockPos(horizontal.getX(), grade, horizontal.getZ());
        }

        boolean isSlopeSection(BlockPos horizontal) {
            if (slopeInteriorAt(horizontal) != null) {
                return true;
            }
            return isSlopeRailCell(route, sectionIndex(horizontal));
        }

        boolean isSlopeSectionAtGrade(BlockPos pos) {
            GradeTransition slope = slopeInteriorAt(pos);
            if (slope != null) {
                return pos.getY() == slope.lowCenter.getY();
            }
            int index = sectionIndex(pos);
            return route.get(index).getY() == pos.getY()
                    && isSlopeRailCell(route, index);
        }

        /** The analytic slope's five clear cells are authoritative at the low grade. */
        private GradeTransition slopeInteriorAt(BlockPos pos) {
            return slopeInteriors.get(xzKey(pos.getX(), pos.getZ()));
        }

        /**
         * Select the third vertical-slab row where the real cardinal wall crosses the midpoint of
         * the slope edge. Normally this is the analytic +/-3 endpoint. If one of the five interior
         * cells was previously attributed to the high slice, compare that endpoint with its
         * high-side neighbour and use whichever is closer to the half-block grade plane.
         */
        Set<Long> raisedSlopeWallColumns(Set<Long> wallBoundary) {
            Set<Long> result = new HashSet<>();
            for (long packed : wallBoundary) {
                BlockPos wall = BlockPos.fromLong(packed);
                if (isSlopeSectionAtGrade(wall)) {
                    result.add(packed);
                }
            }
            for (GradeTransition transition : transitions) {
                if (!boundaryCorrections.contains(transition)) {
                    continue;
                }
                for (int sign = -1; sign <= 1; sign += 2) {
                    BlockPos original = transition.normalOffset(sign * 3);
                    result.remove(original.toLong());
                    BlockPos selected = correctedSlopeWall(
                            transition, original, wallBoundary);
                    if (selected != null) {
                        result.add(selected.toLong());
                    }
                }
            }
            return result;
        }

        Set<BlockPos> correctedSlopeWallCaps(
                Set<Long> wallBoundary, int catenaryY) {
            Set<BlockPos> result = new HashSet<>();
            for (GradeTransition transition : transitions) {
                if (!boundaryCorrections.contains(transition)) {
                    continue;
                }
                for (int sign = -1; sign <= 1; sign += 2) {
                    BlockPos original = transition.normalOffset(sign * 3);
                    BlockPos selected = correctedSlopeWall(
                            transition, original, wallBoundary);
                    if (selected != null && !selected.equals(original)
                            && wallBoundary.contains(original.toLong())) {
                        result.add(transition.normalOffset(sign * 2).up(catenaryY));
                    }
                }
            }
            return result;
        }

        private boolean needsBoundaryCorrection(GradeTransition transition) {
            for (int lateral = -2; lateral <= 2; lateral++) {
                BlockPos interior = transition.normalOffset(lateral);
                if (route.get(sectionIndex(interior)).getY()
                        != transition.lowCenter.getY()) {
                    return true;
                }
            }
            return false;
        }

        private BlockPos correctedSlopeWall(
                GradeTransition transition, BlockPos original,
                Set<Long> wallBoundary) {
            BlockPos shifted = transition.towardHigh(original);
            boolean originalWall = wallBoundary.contains(original.toLong());
            boolean shiftedWall = wallBoundary.contains(shifted.toLong());
            if (!originalWall) {
                return shiftedWall ? shifted : null;
            }
            if (!shiftedWall) {
                return original;
            }
            return transition.midpointDistance(shifted)
                    < transition.midpointDistance(original) ? shifted : original;
        }

        /**
         * Choose the curve point whose tangent-normal section passes closest to this block. The
         * Euclidean nearest route cell supplies a local window so a remote normal elsewhere on a
         * long bend cannot win merely because its infinite line happens to cross the same area.
         */
        int sectionIndex(BlockPos pos) {
            int nearest = closestHorizontalRouteIndex(pos, route);
            int first = Math.max(0, nearest - LOCAL_TRANSITION_PATH_LIMIT);
            int last = Math.min(route.size() - 1,
                    nearest + LOCAL_TRANSITION_PATH_LIMIT);
            int bestIndex = nearest;
            double bestLongitudinal = Double.POSITIVE_INFINITY;
            long bestDistance = Long.MAX_VALUE;
            for (int i = first; i <= last; i++) {
                BlockPos center = route.get(i);
                Vec tangent = tangents.get(i);
                double length = Math.sqrt(
                        tangent.x * tangent.x + tangent.z * tangent.z);
                if (length <= 1.0E-8) {
                    continue;
                }
                double dx = pos.getX() - center.getX();
                double dz = pos.getZ() - center.getZ();
                double longitudinal = Math.abs(
                        (dx * tangent.x + dz * tangent.z) / length);
                long distance = (long) dx * (long) dx + (long) dz * (long) dz;
                if (longitudinal < bestLongitudinal - 1.0E-8
                        || Math.abs(longitudinal - bestLongitudinal) <= 1.0E-8
                        && distance < bestDistance) {
                    bestIndex = i;
                    bestLongitudinal = longitudinal;
                    bestDistance = distance;
                }
            }
            return bestIndex;
        }
    }

    private static final class GradeTransition {
        final BlockPos lowCenter;
        final BlockPos highCenter;
        final double normalX;
        final double normalZ;
        final double highTangentX;
        final double highTangentZ;

        GradeTransition(BlockPos lowCenter, BlockPos highCenter, Vec tangent) {
            this.lowCenter = lowCenter;
            this.highCenter = highCenter;
            // Scale the normal by its dominant component instead of Euclidean length. With a
            // near-45-degree unit normal, round(normal * 1) and round(normal * 2) can identify the
            // same lattice block, collapsing a seven-cell slope section to five cells and placing
            // its endpoint walls inside the clearance. Chebyshev scaling advances at least one
            // coordinate by exactly one block for every lateral index.
            double rasterScale = Math.max(Math.abs(tangent.x), Math.abs(tangent.z));
            normalX = -tangent.z / rasterScale;
            normalZ = tangent.x / rasterScale;
            double tangentLength = Math.sqrt(
                    tangent.x * tangent.x + tangent.z * tangent.z);
            double orientedX = tangent.x / tangentLength;
            double orientedZ = tangent.z / tangentLength;
            double highDot = (highCenter.getX() - lowCenter.getX()) * orientedX
                    + (highCenter.getZ() - lowCenter.getZ()) * orientedZ;
            if (highDot < 0) {
                orientedX = -orientedX;
                orientedZ = -orientedZ;
            }
            highTangentX = orientedX;
            highTangentZ = orientedZ;
        }

        BlockPos normalOffset(int lateral) {
            return new BlockPos(
                    Math.round(lowCenter.getX() + normalX * lateral),
                    lowCenter.getY(),
                    Math.round(lowCenter.getZ() + normalZ * lateral));
        }

        BlockPos towardHigh(BlockPos pos) {
            return pos.add(
                    Integer.signum(highCenter.getX() - lowCenter.getX()), 0,
                    Integer.signum(highCenter.getZ() - lowCenter.getZ()));
        }

        double midpointDistance(BlockPos pos) {
            double dx = pos.getX() - lowCenter.getX();
            double dz = pos.getZ() - lowCenter.getZ();
            double station = dx * highTangentX + dz * highTangentZ;
            return Math.abs(station - 0.5);
        }
    }

    private static final class Geometry {
        final List<BlockPos> route;
        final List<EnumFacing> sectionFacings;
        final List<Vec> sectionTangents;
        final double length;
        final double minimumRadius;
        final double maximumGrade;

        Geometry(List<BlockPos> route, List<EnumFacing> sectionFacings,
                 List<Vec> sectionTangents,
                 double length, double minimumRadius, double maximumGrade) {
            this.route = route;
            this.sectionFacings = sectionFacings;
            this.sectionTangents = sectionTangents;
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
