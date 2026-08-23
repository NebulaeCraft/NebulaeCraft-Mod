package net.kuina.nebulaecraft.autogen.template.tunnel;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenConfig;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.TunnelBuildException;
import net.kuina.nebulaecraft.autogen.route.RouteGeometry;
import net.kuina.nebulaecraft.autogen.route.RoutePlanner;
import net.kuina.nebulaecraft.autogen.route.RouteVector;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.kuina.nebulaecraft.block.BlockAutogenMarker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
import java.util.Map;
import java.util.Set;

/** Builder owned exclusively by the eleven-wide, double-track metro tunnel template. */
public final class MetroTunnel2Builder {
    private static final double MINIMUM_RADIUS = 12.0;
    private static final double MAXIMUM_GRADE = 0.1;
    private static final int CLEAR_HALF_WIDTH = 4;
    private static final int WALL_OFFSET = 5;
    private static final int TRACK_OFFSET = 2;
    private static final int CATENARY_Y = 5;
    private static final int RECESS_Y = 6;
    private static final int ROOF_Y = 7;
    private static final int LOCAL_PATH_LIMIT = 14;

    private MetroTunnel2Builder() {
    }

    public static AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                                    String templateId, String displayName)
            throws AutogenBuildException {
        validateSelection(world, selection);
        RouteGeometry geometry = RoutePlanner.plan(
                selection, MINIMUM_RADIUS, MAXIMUM_GRADE);
        AutogenConfig.Settings settings = AutogenConfig.get();
        if (geometry.length > settings.maxPathLength) {
            throw new TunnelBuildException(
                    "路线长度超过配置上限 " + settings.maxPathLength);
        }

        Materials materials = new Materials();
        DoubleTrackPlan doubleTrack = planDoubleTrack(geometry);
        GradeLayout gradeLayout = doubleTrack.gradeLayout;
        LanePath firstTrack = doubleTrack.firstTrack;
        LanePath secondTrack = doubleTrack.secondTrack;
        TrackbedLayout trackbed = doubleTrack.trackbed;
        Set<Long> clearFloors = createClearFloors(
                geometry, gradeLayout, trackbed);
        Set<Long> wallFloors = createWallFloors(clearFloors, geometry.route);
        Map<Long, EnumFacing> innerWallDirections = findInnerWallDirections(
                clearFloors, wallFloors, geometry.route);
        Set<Long> backingFloors = createCornerBackings(
                clearFloors, wallFloors, geometry.route);
        Set<Long> recessFloors = createRecessFloors(
                geometry, gradeLayout, firstTrack, secondTrack, clearFloors);

        LinkedHashMap<Long, PlannedState> states = new LinkedHashMap<>();
        planShell(states, clearFloors, wallFloors, backingFloors,
                innerWallDirections, recessFloors, firstTrack, secondTrack,
                gradeLayout, materials);
        planTrackbed(states, trackbed, materials);
        clearCenterLine(states, geometry.route);

        SupportLayout supports = createSupportLayout(
                geometry.route.size(), firstTrack, secondTrack,
                settings.catenarySupportSpacing);
        List<AutogenPlan.Operation> railFinalizations = new ArrayList<>();
        placeTrackAndCatenary(states, firstTrack, supports.firstTrackIndices,
                geometry.sectionFacings, materials, railFinalizations);
        placeTrackAndCatenary(states, secondTrack, supports.secondTrackIndices,
                geometry.sectionFacings, materials, railFinalizations);
        orientVerticalSlabsByCurveTangent(states, geometry.route,
                geometry.sectionFacings, materials.verticalSlab);
        placeTunnelLights(states, geometry, gradeLayout, clearFloors,
                innerWallDirections, materials.tunnelLight, settings.lightSpacing);

        if (states.size() > settings.maxChangedBlocks) {
            throw new TunnelBuildException(
                    "预计修改方块数超过配置上限 " + settings.maxChangedBlocks);
        }
        List<PlannedState> ordered = new ArrayList<>(states.values());
        Collections.sort(ordered, Comparator.comparingInt(value -> value.priority));
        List<AutogenPlan.Operation> operations = new ArrayList<>(
                ordered.size() + railFinalizations.size());
        for (PlannedState state : ordered) {
            operations.add(new AutogenPlan.Operation(state.pos, state.state));
        }
        operations.addAll(railFinalizations);

        List<AutogenPlan.PreviewFrame> previewFrames = createPreviewFrames(
                states, geometry.route, geometry.sectionFacings);
        return new AutogenPlan(world.provider.getDimension(), operations,
                new ArrayList<>(geometry.route), previewFrames,
                geometry.length, geometry.minimumRadius, geometry.maximumGrade,
                templateId, displayName, AutogenTemplateKind.TUNNEL);
    }

    /**
     * Plan the shared double-track alignment and trackbed semantics used by tunnel_metro_2.
     * Bridge templates may consume this immutable view without inheriting any tunnel shell.
     */
    public static DoubleTrackPlan planDoubleTrack(RouteGeometry geometry)
            throws TunnelBuildException {
        GradeLayout gradeLayout = new GradeLayout(
                geometry.route, geometry.sectionTangents);
        Set<Long> centerLine = horizontalKeys(geometry.route);
        LanePath firstHorizontalTrack = buildLane(
                geometry, gradeLayout, -TRACK_OFFSET,
                centerLine, Collections.<Long>emptySet(), "左线");
        LanePath secondHorizontalTrack = buildLane(
                geometry, gradeLayout, TRACK_OFFSET,
                centerLine, firstHorizontalTrack.horizontalKeys, "右线");
        LanePath firstTrack = applyLaneGrades(
                firstHorizontalTrack, geometry.route, "左线");
        LanePath secondTrack = applyLaneGrades(
                secondHorizontalTrack, geometry.route, "右线");
        validateParallelTracks(firstTrack, secondTrack, geometry.route);
        TrackbedLayout trackbed = createTrackbedLayout(
                firstTrack, secondTrack, gradeLayout, geometry.route);
        return new DoubleTrackPlan(
                gradeLayout, firstTrack, secondTrack, trackbed);
    }

    private static void validateSelection(WorldServer world,
                                          AutogenSelection.Selection selection)
            throws TunnelBuildException {
        if (selection == null || !selection.isComplete()) {
            throw new TunnelBuildException("请先用自动生成魔杖选择两个标记方块");
        }
        if (selection.start.dimension != selection.end.dimension
                || selection.start.dimension != world.provider.getDimension()) {
            throw new TunnelBuildException("两个标记必须与玩家位于同一维度");
        }
        if (!isMarker(world, selection.start.pos)
                || !isMarker(world, selection.end.pos)) {
            throw new TunnelBuildException("已选择的标记方块不存在或已被移动");
        }
        if (world.getBlockState(selection.start.pos).getValue(BlockAutogenMarker.FACING)
                != selection.start.facing
                || world.getBlockState(selection.end.pos).getValue(BlockAutogenMarker.FACING)
                != selection.end.facing) {
            throw new TunnelBuildException("标记在选择后被旋转，请用魔杖重新选择两个标记");
        }
    }

    /** Build one ordered offset lane, filling only local raster gaps with cardinal cells. */
    private static LanePath buildLane(RouteGeometry geometry, GradeLayout gradeLayout,
                                      int lateral, Set<Long> centerLine,
                                      Set<Long> otherTrack, String laneName)
            throws TunnelBuildException {
        List<LaneCell> cells = new ArrayList<>();
        Map<Integer, Integer> primaryIndices = new HashMap<>();
        Map<Long, Integer> used = new HashMap<>();
        Map<Integer, BlockPos> transitionOverrides = laneTransitionOverrides(
                geometry, lateral);
        for (int section = 0; section < geometry.route.size(); section++) {
            BlockPos candidate = transitionOverrides.get(section);
            if (candidate == null) {
                candidate = gradeLayout.laneSectionCell(
                        section, lateral, geometry.sectionFacings.get(section));
            }
            long candidateKey = xzKey(candidate.getX(), candidate.getZ());
            if (centerLine.contains(candidateKey) || otherTrack.contains(candidateKey)) {
                throw new TunnelBuildException(laneName + "在弯道处侵入另一条线路或中心线");
            }
            if (horizontalDistanceToRoute(candidate, geometry.route) < 2) {
                if (cells.isEmpty()) {
                    throw new TunnelBuildException(laneName + "起点没有内侧道床空间");
                }
                // Around the analytic tangent-axis switch a cardinal offset can land directly
                // beside a later center-route stair cell. Omitting that anchor lets the bounded
                // connector follow the outer diagonal and preserves one cell for the inner 44:0.
                primaryIndices.put(section, cells.size() - 1);
                continue;
            }
            if (cells.isEmpty()) {
                cells.add(new LaneCell(candidate, section));
                used.put(candidateKey, 0);
                primaryIndices.put(section, 0);
                continue;
            }

            BlockPos previous = cells.get(cells.size() - 1).pos;
            if (sameHorizontal(previous, candidate)) {
                if (previous.getY() != candidate.getY()) {
                    throw new TunnelBuildException(laneName + "出现垂直重叠的坡轨");
                }
                primaryIndices.put(section, cells.size() - 1);
                continue;
            }
            Integer existing = used.get(candidateKey);
            if (existing != null) {
                // The inner offset of a rasterized curve may briefly select an already consumed
                // cell. Keeping the current endpoint removes the duplicate without closing a loop.
                primaryIndices.put(section, cells.size() - 1);
                continue;
            }

            List<BlockPos> connection = connectLaneCells(
                    previous, candidate, centerLine, otherTrack,
                    used.keySet(), geometry.route, laneName + "[" + section + "]");
            for (BlockPos pos : connection) {
                long key = xzKey(pos.getX(), pos.getZ());
                if (used.containsKey(key)) {
                    throw new TunnelBuildException(laneName + "在弯道处发生自交");
                }
                used.put(key, cells.size());
                // A tangent-axis connector can span several center sections. Attribute each
                // inserted cell to its nearest analytic section so facilities rotate at the
                // actual slope crossover instead of inheriting one endpoint's facing.
                cells.add(new LaneCell(pos,
                        closestHorizontalRouteIndex(pos, geometry.route)));
            }
            primaryIndices.put(section, cells.size() - 1);
        }

        return new LanePath(cells, primaryIndices, used.keySet());
    }

    /**
     * Stagger the two track shifts around one center-line raster step. The track on the shift side
     * moves one row before the center; the opposite track moves one row after it. This preserves
     * the empty center seam and matches the six-row reference transition.
     */
    private static Map<Integer, BlockPos> laneTransitionOverrides(
            RouteGeometry geometry, int lateral) {
        Map<Integer, BlockPos> result = new HashMap<>();
        Map<Long, Integer> baseOccurrences = new HashMap<>();
        for (int section = 0; section < geometry.route.size(); section++) {
            BlockPos base = horizontalLaneCell(
                    geometry, section, lateral);
            long key = xzKey(base.getX(), base.getZ());
            baseOccurrences.put(key, baseOccurrences.containsKey(key)
                    ? baseOccurrences.get(key) + 1 : 1);
        }
        int distance = Math.abs(lateral);
        for (int i = 1; i + 2 < geometry.route.size(); i++) {
            BlockPos before = geometry.route.get(i - 1);
            BlockPos oldCorner = geometry.route.get(i);
            BlockPos newCorner = geometry.route.get(i + 1);
            BlockPos after = geometry.route.get(i + 2);
            EnumFacing incoming = horizontalDirection(before, oldCorner);
            EnumFacing shift = horizontalDirection(oldCorner, newCorner);
            EnumFacing outgoing = horizontalDirection(newCorner, after);
            if (incoming == null || shift == null || outgoing != incoming
                    || shift.getAxis() == incoming.getAxis()
                    || geometry.sectionFacings.get(i - 1) != incoming
                    || geometry.sectionFacings.get(i) != incoming
                    || geometry.sectionFacings.get(i + 1) != incoming
                    || geometry.sectionFacings.get(i + 2) != incoming) {
                continue;
            }
            EnumFacing laneSide = lateral < 0
                    ? incoming.rotateYCCW() : incoming.rotateY();
            if (shift == laneSide) {
                BlockPos omitted = horizontalLaneCell(geometry, i, lateral);
                if (baseOccurrences.get(xzKey(
                        omitted.getX(), omitted.getZ())) > 1) {
                    continue;
                }
                BlockPos shiftedEarly = oldCorner.offset(laneSide, distance).offset(shift);
                result.put(i, new BlockPos(
                        shiftedEarly.getX(), geometry.route.get(0).getY(),
                        shiftedEarly.getZ()));
            } else if (shift == laneSide.getOpposite()) {
                BlockPos omitted = horizontalLaneCell(geometry, i + 1, lateral);
                if (baseOccurrences.get(xzKey(
                        omitted.getX(), omitted.getZ())) > 1) {
                    continue;
                }
                BlockPos shiftedLate = newCorner.offset(laneSide, distance)
                        .offset(shift.getOpposite());
                result.put(i + 1, new BlockPos(
                        shiftedLate.getX(), geometry.route.get(0).getY(),
                        shiftedLate.getZ()));
            }
        }
        return result;
    }

    private static BlockPos horizontalLaneCell(
            RouteGeometry geometry, int section, int lateral) {
        BlockPos center = geometry.route.get(section);
        EnumFacing facing = geometry.sectionFacings.get(section);
        int nx = -facing.getFrontOffsetZ();
        int nz = facing.getFrontOffsetX();
        return new BlockPos(center.getX() + nx * lateral,
                geometry.route.get(0).getY(), center.getZ() + nz * lateral);
    }

    /** Return the cells after start, including end. */
    private static List<BlockPos> connectLaneCells(
            BlockPos start, BlockPos end, Set<Long> centerLine,
            Set<Long> otherTrack, Set<Long> used, List<BlockPos> centerRoute,
            String laneName) throws TunnelBuildException {
        int directDistance = horizontalManhattanDistance(start, end);
        if (directDistance == 0 || directDistance > LOCAL_PATH_LIMIT) {
            throw new TunnelBuildException(laneName + "在切线换向处无法连续连接");
        }
        if (directDistance == 1) {
            if (Math.abs(start.getY() - end.getY()) > 1) {
                throw new TunnelBuildException(laneName + "坡轨高差超过一格");
            }
            return Collections.singletonList(end);
        }

        int minX = Math.min(start.getX(), end.getX()) - 4;
        int maxX = Math.max(start.getX(), end.getX()) + 4;
        int minZ = Math.min(start.getZ(), end.getZ()) - 4;
        int maxZ = Math.max(start.getZ(), end.getZ()) + 4;
        int maximumDepth = Math.min(LOCAL_PATH_LIMIT, directDistance + 6);
        Deque<BlockPos> queue = new ArrayDeque<>();
        Map<Long, Long> previous = new HashMap<>();
        Map<Long, BlockPos> positions = new HashMap<>();
        Map<Long, Integer> depths = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        long startKey = xzKey(start.getX(), start.getZ());
        long endKey = xzKey(end.getX(), end.getZ());
        queue.add(start);
        visited.add(startKey);
        positions.put(startKey, start);
        depths.put(startKey, 0);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            long currentKey = xzKey(current.getX(), current.getZ());
            int depth = depths.get(currentKey);
            if (depth >= maximumDepth) {
                continue;
            }
            List<EnumFacing> directions = new ArrayList<>(
                    Arrays.asList(EnumFacing.HORIZONTALS));
            Collections.sort(directions, (first, second) -> {
                BlockPos firstPos = current.offset(first);
                BlockPos secondPos = current.offset(second);
                int firstTarget = horizontalManhattanDistance(firstPos, end);
                int secondTarget = horizontalManhattanDistance(secondPos, end);
                if (firstTarget != secondTarget) {
                    return Integer.compare(firstTarget, secondTarget);
                }
                long firstLineError = connectorLineError(firstPos, start, end);
                long secondLineError = connectorLineError(secondPos, start, end);
                if (firstLineError != secondLineError) {
                    return Long.compare(firstLineError, secondLineError);
                }
                // A normal one-cell diagonal has two equally accurate raster phases. Preserve
                // the sampled early/late stagger by choosing the phase farther from the centre.
                // At the tangent-axis crossover, however, the line-error comparison above makes
                // a multi-cell connector alternate its X/Z steps instead of protruding as an
                // equally short but visibly incorrect L-shaped run.
                return Long.compare(
                        horizontalDistanceToRoute(secondPos, centerRoute),
                        horizontalDistanceToRoute(firstPos, centerRoute));
            });
            for (EnumFacing direction : directions) {
                int nextX = current.getX() + direction.getFrontOffsetX();
                int nextZ = current.getZ() + direction.getFrontOffsetZ();
                if (nextX < minX || nextX > maxX || nextZ < minZ || nextZ > maxZ) {
                    continue;
                }
                long nextKey = xzKey(nextX, nextZ);
                if (nextKey != endKey && (centerLine.contains(nextKey)
                        || otherTrack.contains(nextKey) || used.contains(nextKey)
                        || horizontalDistanceToRoute(
                        new BlockPos(nextX, start.getY(), nextZ), centerRoute) < 2)) {
                    continue;
                }
                if (visited.contains(nextKey)) {
                    continue;
                }
                BlockPos next = nextKey == endKey ? end
                        : new BlockPos(nextX, start.getY(), nextZ);
                if (Math.abs(next.getY() - current.getY()) > 1) {
                    continue;
                }
                visited.add(nextKey);
                previous.put(nextKey, currentKey);
                positions.put(nextKey, next);
                depths.put(nextKey, depth + 1);
                if (nextKey == endKey) {
                    return reconstructPath(previous, positions, startKey, endKey);
                }
                queue.addLast(next);
            }
        }
        throw new TunnelBuildException(laneName + "在切线换向处无法连续连接: "
                + start + " -> " + end);
    }

    /** Squared-scale perpendicular error from a candidate cell to the connector's slope. */
    private static long connectorLineError(
            BlockPos candidate, BlockPos start, BlockPos end) {
        long dx = end.getX() - start.getX();
        long dz = end.getZ() - start.getZ();
        long offsetX = candidate.getX() - start.getX();
        long offsetZ = candidate.getZ() - start.getZ();
        long cross = offsetX * dz - offsetZ * dx;
        return cross * cross;
    }

    /** Move each center-route grade step onto the nearest straight edge of one offset lane. */
    private static LanePath applyLaneGrades(
            LanePath horizontalLane, List<BlockPos> centerRoute, String laneName)
            throws TunnelBuildException {
        int startY = centerRoute.get(0).getY();
        int endY = centerRoute.get(centerRoute.size() - 1).getY();
        if (startY == endY) {
            validateLanePath(horizontalLane, laneName);
            return horizontalLane;
        }
        int direction = Integer.signum(endY - startY);
        List<Integer> desiredSections = new ArrayList<>();
        for (int i = 1; i < centerRoute.size(); i++) {
            if (centerRoute.get(i).getY() != centerRoute.get(i - 1).getY()) {
                desiredSections.add(i);
            }
        }
        List<Integer> selectedEdges = selectLaneGradeEdges(
                horizontalLane, desiredSections, direction);
        Set<Integer> selected = new HashSet<>(selectedEdges);
        List<LaneCell> gradedCells = new ArrayList<>(horizontalLane.cells.size());
        int y = startY;
        for (int i = 0; i < horizontalLane.cells.size(); i++) {
            if (selected.contains(i)) {
                y += direction;
            }
            LaneCell source = horizontalLane.cells.get(i);
            gradedCells.add(new LaneCell(
                    new BlockPos(source.pos.getX(), y, source.pos.getZ()),
                    source.sourceSection));
        }
        if (y != endY) {
            throw new TunnelBuildException(laneName + "坡步没有到达终点高度");
        }
        LanePath result = new LanePath(
                gradedCells, horizontalLane.primaryIndices,
                horizontalLane.horizontalKeys);
        validateLanePath(result, laneName);
        return result;
    }

    /** Globally match ordered height steps instead of greedily consuming curve-adjacent edges. */
    private static List<Integer> selectLaneGradeEdges(
            LanePath lane, List<Integer> desiredSections, int gradeDirection)
            throws TunnelBuildException {
        final long unreachable = Long.MAX_VALUE / 4;
        int edgeCount = lane.positions.size();
        int stepCount = desiredSections.size();
        int minimumSpacing = Math.max(1,
                (int) Math.ceil(1.0 / MAXIMUM_GRADE - 1.0E-9));
        boolean[] eligible = new boolean[edgeCount];
        int[] sourceSections = new int[edgeCount];
        for (int edge = 1; edge < edgeCount; edge++) {
            int lowIndex = gradeDirection > 0 ? edge - 1 : edge;
            eligible[edge] = turn(lane.positions, lowIndex) == 0
                    && horizontalManhattanDistance(
                    lane.positions.get(edge - 1), lane.positions.get(edge)) == 1;
            sourceSections[edge] = Math.max(
                    lane.cells.get(edge - 1).sourceSection,
                    lane.cells.get(edge).sourceSection);
        }

        long[] previousCosts = new long[edgeCount];
        Arrays.fill(previousCosts, unreachable);
        int[][] parents = new int[stepCount][edgeCount];
        for (int[] row : parents) Arrays.fill(row, -1);
        for (int edge = 1; edge < edgeCount; edge++) {
            if (eligible[edge]) {
                previousCosts[edge] = laneGradeCost(
                        sourceSections[edge], desiredSections.get(0));
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
                    currentCosts[edge] = bestPreviousCost + laneGradeCost(
                            sourceSections[edge], desiredSections.get(step));
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
            throw new TunnelBuildException(
                    "双轨坡度附近没有足够间距的直线边（至少 "
                            + minimumSpacing + " 格）");
        }
        List<Integer> result = new ArrayList<>(
                Collections.nCopies(stepCount, 0));
        for (int step = stepCount - 1; step >= 0; step--) {
            result.set(step, selected);
            if (step > 0) selected = parents[step][selected];
        }
        return result;
    }

    private static long laneGradeCost(int actualSection, int desiredSection) {
        long distance = Math.abs((long) actualSection - desiredSection);
        return distance * distance;
    }

    private static List<BlockPos> reconstructPath(
            Map<Long, Long> previous, Map<Long, BlockPos> positions,
            long startKey, long endKey) {
        List<BlockPos> reversed = new ArrayList<>();
        long current = endKey;
        while (current != startKey) {
            reversed.add(positions.get(current));
            current = previous.get(current);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static void validateLanePath(LanePath lane, String laneName)
            throws TunnelBuildException {
        if (lane.positions.size() < 2) {
            throw new TunnelBuildException(laneName + "长度不足");
        }
        for (int i = 1; i < lane.positions.size(); i++) {
            BlockPos previous = lane.positions.get(i - 1);
            BlockPos current = lane.positions.get(i);
            if (horizontalManhattanDistance(previous, current) != 1
                    || Math.abs(previous.getY() - current.getY()) > 1) {
                throw new TunnelBuildException(laneName + "不是连续的正交轨道路网");
            }
        }
        for (int i = 0; i < lane.positions.size(); i++) {
            if (isSlopeRailCell(lane.positions, i) && turn(lane.positions, i) != 0) {
                throw new TunnelBuildException(laneName + "的坡轨不能同时承担水平弯轨");
            }
        }
    }

    private static void validateParallelTracks(
            LanePath first, LanePath second, List<BlockPos> centerRoute)
            throws TunnelBuildException {
        Set<Long> occupied = new HashSet<>(first.horizontalKeys);
        occupied.retainAll(second.horizontalKeys);
        if (!occupied.isEmpty()) {
            throw new TunnelBuildException("双轨在弯道处发生重叠");
        }
        int expectedSteps = Math.abs(centerRoute.get(centerRoute.size() - 1).getY()
                - centerRoute.get(0).getY());
        int firstSteps = heightChangeCount(first.positions);
        int secondSteps = heightChangeCount(second.positions);
        if (firstSteps != expectedSteps || secondSteps != expectedSteps) {
            throw new TunnelBuildException("双轨坡步没有与中心路线同步: 中心="
                    + expectedSteps + ", 左线=" + firstSteps + ", 右线=" + secondSteps);
        }
    }

    private static int heightChangeCount(List<BlockPos> positions) {
        int result = 0;
        for (int i = 1; i < positions.size(); i++) {
            if (positions.get(i - 1).getY() != positions.get(i).getY()) {
                result++;
            }
        }
        return result;
    }

    private static TrackbedLayout createTrackbedLayout(
            LanePath first, LanePath second, GradeLayout gradeLayout,
            List<BlockPos> centerRoute) throws TunnelBuildException {
        Set<Long> centerCells = new HashSet<>();
        Map<Long, Boolean> sideCells = new HashMap<>();
        Set<Long> firstOuterCells = new HashSet<>();
        Set<Long> secondOuterCells = new HashSet<>();
        Set<Long> firstInnerCells = new HashSet<>();
        Set<Long> secondInnerCells = new HashSet<>();
        addTrackbedLane(first.positions, gradeLayout, centerRoute,
                centerCells, sideCells, firstOuterCells, firstInnerCells);
        addTrackbedLane(second.positions, gradeLayout, centerRoute,
                centerCells, sideCells, secondOuterCells, secondInnerCells);
        for (long center : centerCells) {
            sideCells.remove(center);
        }
        firstOuterCells.removeAll(centerCells);
        secondOuterCells.removeAll(centerCells);
        firstInnerCells.removeAll(centerCells);
        secondInnerCells.removeAll(centerCells);
        firstInnerCells.removeAll(firstOuterCells);
        secondInnerCells.removeAll(secondOuterCells);
        Set<Long> centerLine = horizontalKeys(centerRoute);
        List<Set<Long>> sideBands = Arrays.asList(
                firstOuterCells, secondOuterCells,
                firstInnerCells, secondInnerCells);
        removeCenterLineSideCells(sideCells, sideBands, centerLine);
        connectOuterSideTrackbed(firstOuterCells, centerCells, sideCells,
                centerLine, centerRoute, gradeLayout);
        connectOuterSideTrackbed(secondOuterCells, centerCells, sideCells,
                centerLine, centerRoute, gradeLayout);
        removeCenterLineSideCells(sideCells, sideBands, centerLine);
        validateOuterSideTrackbed(firstOuterCells, "左线外侧");
        validateOuterSideTrackbed(secondOuterCells, "右线外侧");
        validateInnerSideTrackbed(firstInnerCells, "左线内侧");
        validateInnerSideTrackbed(secondInnerCells, "右线内侧");
        mergeSlopeTrackbedSections(
                centerCells, sideCells, centerLine, gradeLayout);
        return new TrackbedLayout(centerCells, sideCells);
    }

    private static void addTrackbedLane(
            List<BlockPos> lane, GradeLayout gradeLayout,
            List<BlockPos> centerRoute, Set<Long> centerCells,
            Map<Long, Boolean> sideCells, Set<Long> outerCells,
            Set<Long> innerCells) {
        for (int i = 0; i < lane.size(); i++) {
            BlockPos current = lane.get(i);
            centerCells.add(current.toLong());
            long trackDistance = horizontalDistanceToRoute(current, centerRoute);
            Set<EnumFacing> axes = new HashSet<>();
            if (i > 0) {
                EnumFacing direction = horizontalDirection(current, lane.get(i - 1));
                if (direction != null) axes.add(direction);
            }
            if (i + 1 < lane.size()) {
                EnumFacing direction = horizontalDirection(current, lane.get(i + 1));
                if (direction != null) axes.add(direction);
            }
            if (axes.isEmpty()) {
                axes.add(EnumFacing.NORTH);
            }
            for (EnumFacing axis : axes) {
                for (EnumFacing side : new EnumFacing[]{axis.rotateY(), axis.rotateYCCW()}) {
                    BlockPos sideFloor = gradeLayout.floorAt(current.offset(side));
                    long key = sideFloor.toLong();
                    boolean solid = gradeLayout.isSlopeFloor(sideFloor)
                            || (isSlopeRailCell(lane, i)
                            && sideFloor.getY() == current.getY());
                    sideCells.put(key, Boolean.TRUE.equals(sideCells.get(key)) || solid);
                    if (horizontalDistanceToRoute(sideFloor, centerRoute) > trackDistance) {
                        outerCells.add(key);
                    } else {
                        innerCells.add(key);
                    }
                }
            }
        }
    }

    /**
     * Merge the authoritative analytic slope bed after both rasterized track bands are complete.
     * Each rail owns three 43:8 cells at lateral -3..-1 or 1..3. Near a diagonal tangent the
     * Chebyshev normal endpoint may sit one cardinal cell short of the rasterized outer corner;
     * fill that corner only when no existing 43:8/44:0 cell already owns it.
     */
    private static void mergeSlopeTrackbedSections(
            Set<Long> centerCells, Map<Long, Boolean> sideCells,
            Set<Long> centerLine, GradeLayout gradeLayout) {
        for (GradeTransition transition : gradeLayout.transitions) {
            for (int lateral = -3; lateral <= 3; lateral++) {
                if (lateral == 0) continue;
                BlockPos floor = transition.normalOffset(lateral);
                long packed = floor.toLong();
                if (!centerLine.contains(xzKey(floor.getX(), floor.getZ()))
                        && !centerCells.contains(packed)) {
                    sideCells.put(packed, true);
                }
            }
            for (int lateral : new int[]{-3, 3}) {
                BlockPos normal = transition.normalOffset(lateral);
                BlockPos corner = transition.diagonalOffset(lateral);
                if (horizontalManhattanDistance(normal, corner) != 1
                        || centerLine.contains(xzKey(
                        corner.getX(), corner.getZ()))) {
                    continue;
                }
                long packed = corner.toLong();
                if (!centerCells.contains(packed)
                        && !sideCells.containsKey(packed)) {
                    sideCells.put(packed, true);
                }
            }
        }
    }

    /** Never allow an inner side slab to occupy the template's route datum. */
    private static void removeCenterLineSideCells(
            Map<Long, Boolean> sideCells, List<Set<Long>> sideBands,
            Set<Long> centerLine) {
        List<Long> removed = new ArrayList<>();
        for (long packed : sideCells.keySet()) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (centerLine.contains(xzKey(pos.getX(), pos.getZ()))) {
                removed.add(packed);
            }
        }
        for (long packed : removed) {
            sideCells.remove(packed);
            for (Set<Long> sideBand : sideBands) {
                sideBand.remove(packed);
            }
        }
    }

    /**
     * The two side bands outside the double track must be cardinally continuous. At each diagonal
     * raster step, add the farther non-track elbow. The two inner bands deliberately do not use
     * this pass, so their sample-defined diagonal contacts remain weak and the center stays open.
     */
    private static void connectOuterSideTrackbed(
            Set<Long> outerCells, Set<Long> centerCells,
            Map<Long, Boolean> sideCells, Set<Long> centerLine,
            List<BlockPos> centerRoute, GradeLayout gradeLayout) {
        boolean changed;
        do {
            changed = false;
            List<Long> snapshot = new ArrayList<>(outerCells);
            Set<Long> additions = new HashSet<>();
            for (long packed : snapshot) {
                BlockPos first = BlockPos.fromLong(packed);
                for (int dx : new int[]{-1, 1}) {
                    for (int dz : new int[]{-1, 1}) {
                        BlockPos second = gradeLayout.floorAt(first.add(dx, 0, dz));
                        if (!outerCells.contains(second.toLong())) {
                            continue;
                        }
                        BlockPos xElbow = gradeLayout.floorAt(first.add(dx, 0, 0));
                        BlockPos zElbow = gradeLayout.floorAt(first.add(0, 0, dz));
                        if (outerCells.contains(xElbow.toLong())
                                || outerCells.contains(zElbow.toLong())) {
                            continue;
                        }
                        boolean xBlocked = isTrackOrCenterLine(
                                xElbow, centerCells, centerLine);
                        boolean zBlocked = isTrackOrCenterLine(
                                zElbow, centerCells, centerLine);
                        BlockPos bridge;
                        if (xBlocked != zBlocked) {
                            bridge = xBlocked ? zElbow : xElbow;
                        } else if (!xBlocked) {
                            bridge = horizontalDistanceToRoute(xElbow, centerRoute)
                                    >= horizontalDistanceToRoute(zElbow, centerRoute)
                                    ? xElbow : zElbow;
                        } else {
                            continue;
                        }
                        long bridgeKey = bridge.toLong();
                        if (outerCells.contains(bridgeKey)
                                || isTrackOrCenterLine(bridge, centerCells, centerLine)) {
                            continue;
                        }
                        additions.add(bridgeKey);
                    }
                }
            }
            for (long packed : additions) {
                BlockPos bridge = BlockPos.fromLong(packed);
                boolean solid = gradeLayout.isSlopeFloor(bridge);
                sideCells.put(packed,
                        Boolean.TRUE.equals(sideCells.get(packed)) || solid);
                changed |= outerCells.add(packed);
            }
        } while (changed);
    }

    private static boolean isTrackOrCenterLine(
            BlockPos pos, Set<Long> centerCells, Set<Long> centerLine) {
        if (centerLine.contains(xzKey(pos.getX(), pos.getZ()))) {
            return true;
        }
        return centerCells.contains(pos.toLong())
                || centerCells.contains(pos.up().toLong())
                || centerCells.contains(pos.down().toLong());
    }

    private static void validateOuterSideTrackbed(
            Set<Long> outerCells, String name) throws TunnelBuildException {
        if (outerCells.isEmpty()) {
            throw new TunnelBuildException(name + " 44:0 道床为空");
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        long first = outerCells.iterator().next();
        visited.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            BlockPos current = BlockPos.fromLong(queue.removeFirst());
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos adjacent = current.offset(direction);
                for (int dy = -1; dy <= 1; dy++) {
                    long packed = adjacent.up(dy).toLong();
                    if (outerCells.contains(packed) && visited.add(packed)) {
                        queue.addLast(packed);
                    }
                }
            }
        }
        if (visited.size() != outerCells.size()) {
            throw new TunnelBuildException(name + " 44:0 锯齿曲线无法完整连接");
        }
    }

    /** Inner 44:0 bands intentionally use diagonal weak connections, but must never split. */
    private static void validateInnerSideTrackbed(
            Set<Long> innerCells, String name) throws TunnelBuildException {
        if (innerCells.isEmpty()) {
            throw new TunnelBuildException(name + " 44:0 道床为空");
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        long first = innerCells.iterator().next();
        visited.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            BlockPos current = BlockPos.fromLong(queue.removeFirst());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos adjacent = current.add(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        long packed = adjacent.up(dy).toLong();
                        if (innerCells.contains(packed) && visited.add(packed)) {
                            queue.addLast(packed);
                        }
                    }
                }
            }
        }
        if (visited.size() != innerCells.size()) {
            throw new TunnelBuildException(name + " 44:0 锯齿曲线被切断");
        }
    }

    private static Set<Long> createClearFloors(
            RouteGeometry geometry, GradeLayout gradeLayout,
            TrackbedLayout trackbed) {
        Set<Long> result = new HashSet<>();
        for (int i = 0; i < geometry.route.size(); i++) {
            EnumFacing facing = geometry.sectionFacings.get(i);
            for (int lateral = -CLEAR_HALF_WIDTH;
                 lateral <= CLEAR_HALF_WIDTH; lateral++) {
                result.add(gradeLayout.sectionCell(i, lateral, facing).toLong());
            }
        }
        gradeLayout.addSlopeClearFloors(result);
        addTrackbedClearance(result, trackbed, geometry.route);
        return result;
    }

    /** Keep one full clear floor cell between every trackbed cell and the rebuilt wall. */
    private static void addTrackbedClearance(
            Set<Long> clearFloors, TrackbedLayout trackbed,
            List<BlockPos> route) {
        Set<Long> trackbedCells = new HashSet<>(trackbed.centerCells);
        trackbedCells.addAll(trackbed.sideCells.keySet());
        clearFloors.addAll(trackbedCells);
        for (long packed : trackbedCells) {
            BlockPos bed = BlockPos.fromLong(packed);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos buffer = bed.offset(direction);
                if (isBeyondOpenRouteEnd(buffer, route)
                        || containsTrackbedAtOtherGrade(trackbedCells, buffer)) {
                    continue;
                }
                clearFloors.add(buffer.toLong());
            }
        }
    }

    private static boolean containsTrackbedAtOtherGrade(
            Set<Long> trackbedCells, BlockPos pos) {
        return !trackbedCells.contains(pos.toLong())
                && (trackbedCells.contains(pos.up().toLong())
                || trackbedCells.contains(pos.down().toLong()));
    }

    private static Set<Long> createWallFloors(
            Set<Long> clearFloors, List<BlockPos> route) {
        Set<Long> result = new HashSet<>();
        for (long packed : clearFloors) {
            BlockPos clear = BlockPos.fromLong(packed);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos candidate = clear.offset(direction);
                if (!containsAtAdjacentGrade(clearFloors, candidate)
                        && !isBeyondOpenRouteEnd(candidate, route)) {
                    result.add(candidate.toLong());
                }
            }
        }
        return result;
    }

    private static Map<Long, EnumFacing> findInnerWallDirections(
            Set<Long> clearFloors, Set<Long> wallFloors, List<BlockPos> route) {
        Map<Long, EnumFacing> result = new HashMap<>();
        for (long packed : clearFloors) {
            BlockPos clear = BlockPos.fromLong(packed);
            EnumFacing selected = null;
            long bestDistance = Long.MIN_VALUE;
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos wall = clear.offset(direction);
                if (!containsAtAdjacentGrade(wallFloors, wall)) {
                    continue;
                }
                long distance = horizontalDistanceToRoute(wall, route);
                if (selected == null || distance > bestDistance) {
                    selected = direction;
                    bestDistance = distance;
                }
            }
            if (selected != null) {
                result.put(packed, selected);
            }
        }
        return result;
    }

    private static Set<Long> createCornerBackings(
            Set<Long> clearFloors, Set<Long> wallFloors, List<BlockPos> route) {
        Set<Long> result = new HashSet<>();
        int[] signs = {-1, 1};
        for (long packed : clearFloors) {
            BlockPos clear = BlockPos.fromLong(packed);
            for (int dx : signs) {
                for (int dz : signs) {
                    BlockPos corner = clear.add(dx, 0, dz);
                    if (containsAtAdjacentGrade(clearFloors, corner)
                            || isBeyondOpenRouteEnd(corner, route)) {
                        continue;
                    }
                    BlockPos alongX = clear.add(dx, 0, 0);
                    BlockPos alongZ = clear.add(0, 0, dz);
                    if (containsAtAdjacentGrade(wallFloors, alongX)
                            && containsAtAdjacentGrade(wallFloors, alongZ)) {
                        result.add(corner.toLong());
                    }
                }
            }
        }
        result.removeAll(wallFloors);
        return result;
    }

    private static Set<Long> createRecessFloors(
            RouteGeometry geometry, GradeLayout gradeLayout,
            LanePath firstTrack, LanePath secondTrack,
            Set<Long> clearFloors) {
        Set<Long> result = new HashSet<>();
        for (int i = 0; i < geometry.route.size(); i++) {
            EnumFacing facing = geometry.sectionFacings.get(i);
            for (int lateral = -TRACK_OFFSET; lateral <= TRACK_OFFSET; lateral++) {
                result.add(gradeLayout.sectionCell(i, lateral, facing).toLong());
            }
        }
        for (BlockPos pos : firstTrack.positions) result.add(pos.toLong());
        for (BlockPos pos : secondTrack.positions) result.add(pos.toLong());
        gradeLayout.addSlopeRecessBridges(result, clearFloors);
        return result;
    }

    private static void planShell(
            Map<Long, PlannedState> states, Set<Long> clearFloors,
            Set<Long> wallFloors, Set<Long> backingFloors,
            Map<Long, EnumFacing> innerWallDirections, Set<Long> recessFloors,
            LanePath firstTrack, LanePath secondTrack, GradeLayout gradeLayout,
            Materials materials) throws TunnelBuildException {
        // Exact normal section, route datum at trackbed level (bottom to top):
        // CCCCCCCCCCC / CCSDS.SDSCC / CV.R...R.VC / C.........C /
        // CL.......LC / CV.......VC / CC.K...K.CC / CCCS...SCCC / CCCCCCCCCCC.
        // Curve slices contribute clearance first; the shell is rebuilt around their union.
        Set<Long> outerFloors = new HashSet<>(clearFloors);
        outerFloors.addAll(wallFloors);
        outerFloors.addAll(backingFloors);
        for (long packed : outerFloors) {
            BlockPos floor = BlockPos.fromLong(packed);
            int lift = gradeLayout.shellLift(floor);
            put(states, floor.down(), materials.concrete, 10);
            put(states, floor.up(RECESS_Y + lift), materials.concrete, 10);
            put(states, floor.up(ROOF_Y + lift), materials.concrete, 10);
        }
        Set<Long> structuralWalls = new HashSet<>(wallFloors);
        structuralWalls.addAll(backingFloors);
        for (long packed : structuralWalls) {
            BlockPos floor = BlockPos.fromLong(packed);
            int lift = gradeLayout.shellLift(floor);
            for (int y = 0; y <= RECESS_Y + lift; y++) {
                put(states, floor.up(y), materials.concrete, 10);
            }
        }
        for (long packed : clearFloors) {
            BlockPos floor = BlockPos.fromLong(packed);
            int lift = gradeLayout.shellLift(floor);
            for (int y = 0; y <= CATENARY_Y + lift; y++) {
                put(states, floor.up(y), Blocks.AIR.getDefaultState(), 20);
            }
        }
        for (Map.Entry<Long, EnumFacing> entry : innerWallDirections.entrySet()) {
            BlockPos floor = BlockPos.fromLong(entry.getKey());
            EnumFacing outward = entry.getValue();
            int lift = gradeLayout.shellLift(floor);
            IBlockState verticalSlab = sideMountedState(
                    materials.verticalSlab, outward);
            put(states, floor, materials.concrete, 31);
            put(states, floor.up(), verticalSlab, 32);
            put(states, floor.up(4 + lift), verticalSlab, 32);
            put(states, floor.up(5 + lift), materials.concrete, 31);
        }
        for (long packed : recessFloors) {
            BlockPos floor = BlockPos.fromLong(packed);
            put(states, floor.up(RECESS_Y + gradeLayout.shellLift(floor)),
                    Blocks.AIR.getDefaultState(), 25);
        }
        for (LanePath lane : new LanePath[]{firstTrack, secondTrack}) {
            for (BlockPos floor : lane.positions) {
                put(states, floor.up(RECESS_Y + gradeLayout.shellLift(floor)),
                        materials.horizontalSlab, 33);
            }
        }
    }

    private static void planTrackbed(
            Map<Long, PlannedState> states, TrackbedLayout trackbed,
            Materials materials) {
        for (Map.Entry<Long, Boolean> entry : trackbed.sideCells.entrySet()) {
            put(states, BlockPos.fromLong(entry.getKey()),
                    entry.getValue() ? materials.centerTrackbed : materials.sideTrackbed, 40);
        }
        for (long packed : trackbed.centerCells) {
            put(states, BlockPos.fromLong(packed), materials.centerTrackbed, 41);
        }
    }

    private static void clearCenterLine(
            Map<Long, PlannedState> states, List<BlockPos> route) {
        for (BlockPos center : route) {
            put(states, center, Blocks.AIR.getDefaultState(), 45);
        }
    }

    private static SupportLayout createSupportLayout(
            int sectionCount, LanePath first, LanePath second, int spacing) {
        Set<Integer> firstIndices = new HashSet<>();
        Set<Integer> secondIndices = new HashSet<>();
        Set<Integer> usedFirst = new HashSet<>();
        Set<Integer> usedSecond = new HashSet<>();
        int safeSpacing = Math.max(1, spacing);
        for (int requested = 0; requested < sectionCount; requested += safeSpacing) {
            int[] candidates = {requested, requested - 1, requested + 1,
                    requested - 2, requested + 2};
            for (int section : candidates) {
                Integer firstIndex = first.primaryIndices.get(section);
                Integer secondIndex = second.primaryIndices.get(section);
                if (firstIndex == null || secondIndex == null
                        || usedFirst.contains(firstIndex) || usedSecond.contains(secondIndex)
                        || !isStraightLevelCell(first.positions, firstIndex)
                        || !isStraightLevelCell(second.positions, secondIndex)) {
                    continue;
                }
                firstIndices.add(firstIndex);
                secondIndices.add(secondIndex);
                usedFirst.add(firstIndex);
                usedSecond.add(secondIndex);
                break;
            }
        }
        return new SupportLayout(firstIndices, secondIndices);
    }

    private static boolean isStraightLevelCell(List<BlockPos> route, int index) {
        return index >= 0 && index < route.size()
                && turn(route, index) == 0 && !isSlopeRailCell(route, index);
    }

    private static void placeTrackAndCatenary(
            Map<Long, PlannedState> states, LanePath lane, Set<Integer> supports,
            List<EnumFacing> sectionFacings, Materials materials,
            List<AutogenPlan.Operation> railFinalizations)
            throws TunnelBuildException {
        for (int i = 0; i < lane.positions.size(); i++) {
            BlockPos floor = lane.positions.get(i);
            IBlockState rail = materials.reinforcedTrack.getStateFromMeta(
                    railMeta(lane.positions, i));
            put(states, floor.up(), rail, 50);
            railFinalizations.add(new AutogenPlan.Operation(floor.up(), rail, false));
            IBlockState catenary = catenaryState(
                    lane, i, sectionFacings, supports.contains(i), materials);
            put(states, floor.up(CATENARY_Y), catenary, 50);
        }
    }

    private static void placeTunnelLights(
            Map<Long, PlannedState> states, RouteGeometry geometry,
            GradeLayout gradeLayout, Set<Long> clearFloors,
            Map<Long, EnumFacing> innerWallDirections, Block light,
            int spacing) throws TunnelBuildException {
        int safeSpacing = Math.max(1, spacing);
        for (int i = 0; i < geometry.route.size(); i += safeSpacing) {
            BlockPos center = geometry.route.get(i);
            EnumFacing forward = geometry.sectionFacings.get(i);
            for (int sign = -1; sign <= 1; sign += 2) {
                EnumFacing outward = sign < 0 ? forward.rotateYCCW() : forward.rotateY();
                BlockPos inner = null;
                for (int distance = 0; distance <= WALL_OFFSET + 3; distance++) {
                    BlockPos horizontal = center.offset(outward, distance);
                    BlockPos floor = gradeLayout.floorAt(horizontal);
                    if (!clearFloors.contains(floor.toLong())) {
                        break;
                    }
                    inner = floor;
                }
                if (inner == null) {
                    continue;
                }
                if (!innerWallDirections.containsKey(inner.toLong())) {
                    continue;
                }
                // Placement follows the final clear boundary, but model orientation follows the
                // smooth curve normal rather than a stair-step wall neighbour.
                put(states, inner.up(3), sideMountedState(light, outward), 60);
            }
        }
    }

    private static IBlockState catenaryState(
            LanePath lane, int index, List<EnumFacing> sectionFacings,
            boolean support, Materials materials)
            throws TunnelBuildException {
        List<BlockPos> route = lane.positions;
        int sourceSection = lane.cells.get(index).sourceSection;
        EnumFacing facing = sectionFacings.get(Math.max(0,
                Math.min(sourceSection, sectionFacings.size() - 1)));
        Block block;
        if (support) {
            block = materials.catenarySupport;
        } else if (isSlopeRailCell(route, index)) {
            block = materials.catenarySlope;
            if (gradeDirection(route, index) < 0) {
                facing = facing.getOpposite();
            }
        } else if (turn(route, index) != 0) {
            block = materials.catenaryDiagonal;
            facing = cornerFacing(route, index);
        } else {
            block = materials.catenaryStraight;
        }
        return nebulaFacingState(block, facing);
    }

    /** Apply the same analytic-tangent normal used by tunnel_metro_1 to every final wall slab. */
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
            IBlockState oriented = sideMountedState(
                    verticalSlab, inward.getOpposite());
            entry.setValue(new PlannedState(
                    planned.pos, oriented, planned.priority));
        }
    }

    private static EnumFacing curveNormalTowardRoute(
            BlockPos wall, int routeIndex, List<BlockPos> route,
            List<EnumFacing> sectionFacings) {
        EnumFacing firstNormal = sectionFacings.get(routeIndex).rotateY();
        List<EnumFacing> normals = Arrays.asList(
                firstNormal, firstNormal.getOpposite());
        return closestHorizontalRouteDirection(wall, normals, route);
    }

    private static EnumFacing closestHorizontalRouteDirection(
            BlockPos wall, List<EnumFacing> directions, List<BlockPos> route) {
        EnumFacing best = directions.get(0);
        long bestDistance = Long.MAX_VALUE;
        for (EnumFacing direction : directions) {
            long distance = horizontalDistanceToRoute(
                    wall.offset(direction), route);
            if (distance < bestDistance) {
                best = direction;
                bestDistance = distance;
            }
        }
        return best;
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
            if (up == EnumFacing.EAST) {
                return BlockRailBase.EnumRailDirection.ASCENDING_EAST.getMetadata();
            }
            if (up == EnumFacing.WEST) {
                return BlockRailBase.EnumRailDirection.ASCENDING_WEST.getMetadata();
            }
            if (up == EnumFacing.NORTH) {
                return BlockRailBase.EnumRailDirection.ASCENDING_NORTH.getMetadata();
            }
            if (up == EnumFacing.SOUTH) {
                return BlockRailBase.EnumRailDirection.ASCENDING_SOUTH.getMetadata();
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

    private static List<AutogenPlan.PreviewFrame> createPreviewFrames(
            Map<Long, PlannedState> states, List<BlockPos> route,
            List<EnumFacing> sectionFacings) {
        int scanLateral = WALL_OFFSET + 4;
        int columnCount = scanLateral * 2 + 1;
        List<AutogenPlan.PreviewFrame> frames = new ArrayList<>(route.size());
        for (int i = 0; i < route.size(); i++) {
            BlockPos center = route.get(i);
            EnumFacing forward = sectionFacings.get(i);
            int nx = -forward.getFrontOffsetZ();
            int nz = forward.getFrontOffsetX();
            int[] minY = new int[columnCount];
            int[] maxY = new int[columnCount];
            Arrays.fill(minY, Integer.MAX_VALUE);
            Arrays.fill(maxY, Integer.MIN_VALUE);
            for (int lateral = -scanLateral; lateral <= scanLateral; lateral++) {
                int column = lateral + scanLateral;
                for (int dy = -2; dy <= ROOF_Y + 4; dy++) {
                    BlockPos pos = center.add(nx * lateral, dy, nz * lateral);
                    PlannedState state = states.get(pos.toLong());
                    if (state != null && state.state.getBlock() != Blocks.AIR) {
                        minY[column] = Math.min(minY[column], pos.getY());
                        maxY[column] = Math.max(maxY[column], pos.getY());
                    }
                }
            }
            int left = findOuterColumn(minY, maxY, true);
            int right = findOuterColumn(minY, maxY, false);
            if (left < 0 || right < 0 || left >= right) {
                left = scanLateral - WALL_OFFSET;
                right = scanLateral + WALL_OFFSET;
                minY[left] = center.getY() - 1;
                maxY[left] = center.getY() + ROOF_Y;
                minY[right] = center.getY() - 1;
                maxY[right] = center.getY() + ROOF_Y;
            }
            int leftLateral = left - scanLateral;
            int rightLateral = right - scanLateral;
            double sectionX = center.getX() + 0.5D;
            double sectionZ = center.getZ() + 0.5D;
            if (i == 0) {
                sectionX -= forward.getFrontOffsetX() * 0.5D;
                sectionZ -= forward.getFrontOffsetZ() * 0.5D;
            } else if (i == route.size() - 1) {
                sectionX += forward.getFrontOffsetX() * 0.5D;
                sectionZ += forward.getFrontOffsetZ() * 0.5D;
            }
            double leftX = sectionX + nx * (leftLateral - 0.5D);
            double leftZ = sectionZ + nz * (leftLateral - 0.5D);
            double rightX = sectionX + nx * (rightLateral + 0.5D);
            double rightZ = sectionZ + nz * (rightLateral + 0.5D);
            boolean ring = i == 0 || i == route.size() - 1 || i % 8 == 0
                    || previewFrameChanged(route, sectionFacings, i);
            frames.add(new AutogenPlan.PreviewFrame(
                    new Vec3d(leftX, minY[left], leftZ),
                    new Vec3d(leftX, maxY[left] + 1.0D, leftZ),
                    new Vec3d(rightX, maxY[right] + 1.0D, rightZ),
                    new Vec3d(rightX, minY[right], rightZ), ring));
        }
        return frames;
    }

    private static int findOuterColumn(int[] minY, int[] maxY, boolean fromLeft) {
        int index = fromLeft ? 0 : minY.length - 1;
        int step = fromLeft ? 1 : -1;
        while (index >= 0 && index < minY.length) {
            if (minY[index] != Integer.MAX_VALUE
                    && maxY[index] - minY[index] >= ROOF_Y - 1) {
                return index;
            }
            index += step;
        }
        return -1;
    }

    private static boolean previewFrameChanged(
            List<BlockPos> route, List<EnumFacing> facings, int index) {
        if (index == 0) return true;
        return route.get(index - 1).getY() != route.get(index).getY()
                || facings.get(index - 1).getAxis() != facings.get(index).getAxis();
    }

    private static boolean containsAtAdjacentGrade(Set<Long> positions, BlockPos pos) {
        return positions.contains(pos.toLong())
                || positions.contains(pos.up().toLong())
                || positions.contains(pos.down().toLong());
    }

    private static boolean isBeyondOpenRouteEnd(BlockPos pos, List<BlockPos> route) {
        if (route.size() < 2) return false;
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

    private static long horizontalDistanceToRoute(BlockPos pos, List<BlockPos> route) {
        long best = Long.MAX_VALUE;
        for (BlockPos center : route) {
            long dx = pos.getX() - center.getX();
            long dz = pos.getZ() - center.getZ();
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static int closestHorizontalRouteIndex(BlockPos pos, List<BlockPos> route) {
        int selected = 0;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            long dx = pos.getX() - route.get(i).getX();
            long dz = pos.getZ() - route.get(i).getZ();
            long distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                selected = i;
            }
        }
        return selected;
    }

    private static IBlockState sideMountedState(Block block, EnumFacing attachmentSide)
            throws TunnelBuildException {
        return nebulaFacingState(block, attachmentSide.getOpposite());
    }

    private static IBlockState nebulaFacingState(Block block, EnumFacing facing)
            throws TunnelBuildException {
        switch (facing) {
            case NORTH: return block.getStateFromMeta(0);
            case SOUTH: return block.getStateFromMeta(1);
            case WEST: return block.getStateFromMeta(2);
            case EAST: return block.getStateFromMeta(3);
            default: throw new TunnelBuildException("自动生成方块需要水平方向");
        }
    }

    private static EnumFacing cornerFacing(List<BlockPos> route, int index)
            throws TunnelBuildException {
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
        if (index + 1 < route.size()
                && route.get(index + 1).getY() != current.getY()) {
            return Integer.signum(route.get(index + 1).getY() - current.getY());
        }
        if (index > 0 && route.get(index - 1).getY() != current.getY()) {
            return Integer.signum(current.getY() - route.get(index - 1).getY());
        }
        return 0;
    }

    private static boolean isSlopeRailCell(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        return index > 0 && route.get(index - 1).getY() > current.getY()
                || index + 1 < route.size() && route.get(index + 1).getY() > current.getY();
    }

    private static int horizontalManhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getZ() - second.getZ());
    }

    private static boolean sameHorizontal(BlockPos first, BlockPos second) {
        return first.getX() == second.getX() && first.getZ() == second.getZ();
    }

    private static Set<Long> horizontalKeys(List<BlockPos> positions) {
        Set<Long> result = new HashSet<>();
        for (BlockPos pos : positions) result.add(xzKey(pos.getX(), pos.getZ()));
        return result;
    }

    private static long xzKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static boolean isMarker(WorldServer world, BlockPos pos) {
        ResourceLocation name = world.getBlockState(pos).getBlock().getRegistryName();
        return name != null && name.toString().equals("nebulaecraft:autogen_marker");
    }

    private static IBlockState resolveState(String descriptor)
            throws TunnelBuildException {
        String[] parts = descriptor.split("@", 2);
        Block block = resolveBlock(descriptor);
        int meta = parts.length == 2 ? parseMeta(parts[1], descriptor) : 0;
        return block.getStateFromMeta(meta);
    }

    private static Block resolveBlock(String descriptor) throws TunnelBuildException {
        String id = descriptor.split("@", 2)[0];
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR && !id.equals("minecraft:air")) {
            throw new TunnelBuildException("找不到所需方块: " + id);
        }
        return block;
    }

    private static int parseMeta(String value, String descriptor)
            throws TunnelBuildException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new TunnelBuildException("无效方块 metadata: " + descriptor);
        }
    }

    private static void put(Map<Long, PlannedState> states, BlockPos pos,
                            IBlockState state, int priority) {
        long key = pos.toLong();
        PlannedState current = states.get(key);
        if (current == null || priority >= current.priority) {
            states.put(key, new PlannedState(pos, state, priority));
        }
    }

    /** Assign lateral cells to low/high floors at each one-block grade transition. */
    private static final class GradeLayout {
        final List<BlockPos> route;
        final List<RouteVector> tangents;
        final List<GradeTransition> transitions = new ArrayList<>();
        final Map<Integer, GradeTransition> transitionsByLowIndex = new HashMap<>();
        final Map<Long, GradeTransition> slopeClearCells = new HashMap<>();
        final Map<Long, GradeTransition> slopeShellCells = new HashMap<>();

        GradeLayout(List<BlockPos> route, List<RouteVector> tangents) {
            this.route = route;
            this.tangents = tangents;
            for (int i = 1; i < route.size(); i++) {
                int deltaY = route.get(i).getY() - route.get(i - 1).getY();
                if (deltaY == 0) continue;
                int lowIndex = deltaY > 0 ? i - 1 : i;
                int highIndex = deltaY > 0 ? i : i - 1;
                GradeTransition transition = new GradeTransition(
                        lowIndex, route.get(lowIndex), route.get(highIndex),
                        tangents.get(lowIndex));
                transitions.add(transition);
                transitionsByLowIndex.put(lowIndex, transition);
                for (int lateral = -CLEAR_HALF_WIDTH;
                     lateral <= CLEAR_HALF_WIDTH; lateral++) {
                    BlockPos pos = transition.normalOffset(lateral);
                    slopeClearCells.put(xzKey(pos.getX(), pos.getZ()), transition);
                }
                for (int lateral = -WALL_OFFSET; lateral <= WALL_OFFSET; lateral++) {
                    BlockPos pos = transition.normalOffset(lateral);
                    slopeShellCells.put(xzKey(pos.getX(), pos.getZ()), transition);
                }
            }
        }

        BlockPos sectionCell(int section, int lateral, EnumFacing facing) {
            GradeTransition transition = transitionsByLowIndex.get(section);
            if (transition != null) {
                return transition.normalOffset(lateral);
            }
            BlockPos center = route.get(section);
            int nx = -facing.getFrontOffsetZ();
            int nz = facing.getFrontOffsetX();
            return floorAt(center.add(nx * lateral, 0, nz * lateral));
        }

        /** Horizontal lane geometry is completed before any height steps are assigned. */
        BlockPos laneSectionCell(int section, int lateral, EnumFacing facing) {
            BlockPos center = route.get(section);
            int nx = -facing.getFrontOffsetZ();
            int nz = facing.getFrontOffsetX();
            BlockPos horizontal = center.add(nx * lateral, 0, nz * lateral);
            return new BlockPos(horizontal.getX(), route.get(0).getY(), horizontal.getZ());
        }

        BlockPos floorAt(BlockPos horizontal) {
            GradeTransition transition = slopeClearCells.get(
                    xzKey(horizontal.getX(), horizontal.getZ()));
            if (transition != null) {
                return new BlockPos(horizontal.getX(), transition.lowCenter.getY(),
                        horizontal.getZ());
            }
            int grade = route.get(sectionIndex(horizontal)).getY();
            return new BlockPos(horizontal.getX(), grade, horizontal.getZ());
        }

        boolean isSlopeFloor(BlockPos floor) {
            GradeTransition transition = slopeClearCells.get(
                    xzKey(floor.getX(), floor.getZ()));
            return transition != null && floor.getY() == transition.lowCenter.getY();
        }

        int shellLift(BlockPos floor) {
            GradeTransition transition = slopeShellCells.get(
                    xzKey(floor.getX(), floor.getZ()));
            if (transition != null) {
                return floor.getY() == transition.lowCenter.getY() ? 1 : 0;
            }
            int section = sectionIndex(floor);
            transition = transitionsByLowIndex.get(section);
            return transition != null
                    && floor.getY() == route.get(section).getY() ? 1 : 0;
        }

        void addSlopeClearFloors(Set<Long> result) {
            for (GradeTransition transition : transitions) {
                for (int lateral = -CLEAR_HALF_WIDTH;
                     lateral <= CLEAR_HALF_WIDTH; lateral++) {
                    result.add(transition.normalOffset(lateral).toLong());
                }
            }
        }

        /**
         * Keep the central recess connected where an analytic slope normal crosses the cardinal
         * curve raster. Near a diagonal tangent the normal's central cells touch only at corners,
         * while adjacent level sections contribute cardinal clear cells on both grades. Without
         * these bridge cells the default recess-height concrete remains inside the tunnel.
         */
        void addSlopeRecessBridges(Set<Long> recessFloors,
                                   Set<Long> clearFloors) {
            for (GradeTransition transition : transitions) {
                addSlopeRecessBridge(recessFloors, clearFloors,
                        transition.lowCenter, transition.lowCenter.getY());
                addSlopeRecessBridge(recessFloors, clearFloors,
                        transition.lowCenter, transition.highCenter.getY());
            }
        }

        private void addSlopeRecessBridge(Set<Long> recessFloors,
                                          Set<Long> clearFloors,
                                          BlockPos center, int floorY) {
            BlockPos floor = new BlockPos(center.getX(), floorY, center.getZ());
            addRecessIfClear(recessFloors, clearFloors, floor);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                addRecessIfClear(recessFloors, clearFloors,
                        floor.offset(direction));
            }
        }

        private void addRecessIfClear(Set<Long> recessFloors,
                                      Set<Long> clearFloors, BlockPos floor) {
            long packed = floor.toLong();
            if (clearFloors.contains(packed)) {
                recessFloors.add(packed);
            }
        }

        int sectionIndex(BlockPos pos) {
            int nearest = closestHorizontalRouteIndex(pos, route);
            int first = Math.max(0, nearest - LOCAL_PATH_LIMIT);
            int last = Math.min(route.size() - 1, nearest + LOCAL_PATH_LIMIT);
            int selected = nearest;
            double bestLongitudinal = Double.POSITIVE_INFINITY;
            long bestDistance = Long.MAX_VALUE;
            for (int i = first; i <= last; i++) {
                BlockPos center = route.get(i);
                RouteVector tangent = tangents.get(i);
                double length = Math.sqrt(
                        tangent.x * tangent.x + tangent.z * tangent.z);
                if (length <= 1.0E-8) continue;
                double dx = pos.getX() - center.getX();
                double dz = pos.getZ() - center.getZ();
                double longitudinal = Math.abs(
                        (dx * tangent.x + dz * tangent.z) / length);
                long distance = (long) dx * (long) dx + (long) dz * (long) dz;
                if (longitudinal < bestLongitudinal - 1.0E-8
                        || Math.abs(longitudinal - bestLongitudinal) <= 1.0E-8
                        && distance < bestDistance) {
                    selected = i;
                    bestLongitudinal = longitudinal;
                    bestDistance = distance;
                }
            }
            return selected;
        }
    }

    private static final class GradeTransition {
        final int lowIndex;
        final BlockPos lowCenter;
        final BlockPos highCenter;
        final double normalX;
        final double normalZ;

        GradeTransition(int lowIndex, BlockPos lowCenter, BlockPos highCenter,
                        RouteVector tangent) {
            this.lowIndex = lowIndex;
            this.lowCenter = lowCenter;
            this.highCenter = highCenter;
            double scale = Math.max(Math.abs(tangent.x), Math.abs(tangent.z));
            normalX = -tangent.z / scale;
            normalZ = tangent.x / scale;
        }

        BlockPos normalOffset(int lateral) {
            return new BlockPos(
                    Math.round(lowCenter.getX() + normalX * lateral),
                    lowCenter.getY(),
                    Math.round(lowCenter.getZ() + normalZ * lateral));
        }

        BlockPos diagonalOffset(int lateral) {
            return lowCenter.add(
                    (int) Math.signum(normalX) * lateral, 0,
                    (int) Math.signum(normalZ) * lateral);
        }
    }

    private static final class Materials {
        final IBlockState concrete;
        final IBlockState horizontalSlab;
        final Block verticalSlab;
        final IBlockState centerTrackbed;
        final IBlockState sideTrackbed;
        final Block reinforcedTrack;
        final Block tunnelLight;
        final Block catenaryStraight;
        final Block catenarySupport;
        final Block catenaryDiagonal;
        final Block catenarySlope;

        Materials() throws TunnelBuildException {
            concrete = resolveState("railcraft:reinforced_concrete@8");
            horizontalSlab = resolveState(
                    "nebulaecraft:reinforced_concrete_slab@1");
            verticalSlab = resolveBlock(
                    "nebulaecraft:reinforced_concrete_vertical_slab@0");
            centerTrackbed = resolveState("minecraft:double_stone_slab@8");
            sideTrackbed = resolveState("minecraft:stone_slab@0");
            reinforcedTrack = resolveBlock("railcraft:track_flex_reinforced@0");
            tunnelLight = resolveBlock("nebulaecraft:tunnel_light@0");
            catenaryStraight = resolveBlock("nebulaecraft:catenary_steel@0");
            catenarySupport = resolveBlock("nebulaecraft:catenary_steel_support@0");
            catenaryDiagonal = resolveBlock("nebulaecraft:catenary_steel_diagonal@0");
            catenarySlope = resolveBlock("nebulaecraft:catenary_steel_slope@0");
        }
    }

    /** Read-only bridge-facing view of the tunnel_metro_2 lane and trackbed planner. */
    public static final class DoubleTrackPlan {
        private final GradeLayout gradeLayout;
        private final LanePath firstTrack;
        private final LanePath secondTrack;
        private final TrackbedLayout trackbed;

        private DoubleTrackPlan(GradeLayout gradeLayout,
                                LanePath firstTrack, LanePath secondTrack,
                                TrackbedLayout trackbed) {
            this.gradeLayout = gradeLayout;
            this.firstTrack = firstTrack;
            this.secondTrack = secondTrack;
            this.trackbed = trackbed;
        }

        public List<BlockPos> getFirstTrack() {
            return Collections.unmodifiableList(firstTrack.positions);
        }

        public List<BlockPos> getSecondTrack() {
            return Collections.unmodifiableList(secondTrack.positions);
        }

        public int getFirstSourceSection(int trackIndex) {
            return firstTrack.cells.get(trackIndex).sourceSection;
        }

        public int getSecondSourceSection(int trackIndex) {
            return secondTrack.cells.get(trackIndex).sourceSection;
        }

        public Set<Long> getCenterTrackbedCells() {
            return Collections.unmodifiableSet(trackbed.centerCells);
        }

        public Map<Long, Boolean> getSideTrackbedCells() {
            return Collections.unmodifiableMap(trackbed.sideCells);
        }

        public BlockPos floorAt(BlockPos horizontal) {
            return gradeLayout.floorAt(horizontal);
        }
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

    private static final class LaneCell {
        final BlockPos pos;
        final int sourceSection;

        LaneCell(BlockPos pos, int sourceSection) {
            this.pos = pos;
            this.sourceSection = sourceSection;
        }
    }

    private static final class LanePath {
        final List<LaneCell> cells;
        final List<BlockPos> positions;
        final Map<Integer, Integer> primaryIndices;
        final Set<Long> horizontalKeys;

        LanePath(List<LaneCell> cells, Map<Integer, Integer> primaryIndices,
                 Set<Long> horizontalKeys) {
            this.cells = cells;
            this.primaryIndices = primaryIndices;
            this.horizontalKeys = new HashSet<>(horizontalKeys);
            positions = new ArrayList<>(cells.size());
            for (LaneCell cell : cells) positions.add(cell.pos);
        }
    }

    private static final class TrackbedLayout {
        final Set<Long> centerCells;
        final Map<Long, Boolean> sideCells;

        TrackbedLayout(Set<Long> centerCells, Map<Long, Boolean> sideCells) {
            this.centerCells = centerCells;
            this.sideCells = sideCells;
        }
    }

    private static final class SupportLayout {
        final Set<Integer> firstTrackIndices;
        final Set<Integer> secondTrackIndices;

        SupportLayout(Set<Integer> firstTrackIndices,
                      Set<Integer> secondTrackIndices) {
            this.firstTrackIndices = firstTrackIndices;
            this.secondTrackIndices = secondTrackIndices;
        }
    }
}
