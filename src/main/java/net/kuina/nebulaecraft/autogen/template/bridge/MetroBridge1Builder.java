package net.kuina.nebulaecraft.autogen.template.bridge;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenConfig;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/** Builder owned exclusively by the five-wide, single-track metro bridge template. */
public final class MetroBridge1Builder {
    private static final double MINIMUM_RADIUS = 12.0;
    private static final double MAXIMUM_GRADE = 0.1;
    private static final int HALF_WIDTH = 2;
    private static final int INNER_HALF_WIDTH = 1;
    private static final int TRACK_Y = 1;
    private static final int CATENARY_Y = 5;
    private static final int REPEAT_LENGTH = 9;
    private static final int LOCAL_TRANSITION_PATH_LIMIT = 7;

    private MetroBridge1Builder() {
    }

    public static AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                                    String templateId, String displayName)
            throws AutogenBuildException {
        validateSelection(world, selection);
        RouteGeometry geometry = RoutePlanner.plan(
                selection, MINIMUM_RADIUS, MAXIMUM_GRADE);
        AutogenConfig.Settings settings = AutogenConfig.get();
        if (geometry.length > settings.maxPathLength) {
            throw new AutogenBuildException(
                    "路线长度超过配置上限 " + settings.maxPathLength);
        }
        validateBuildHeight(world, geometry.route);

        Materials materials = new Materials();
        GradeLayout gradeLayout = new GradeLayout(
                geometry.route, geometry.sectionTangents);
        Footprint footprint = createFootprint(geometry.route, gradeLayout);
        LinkedHashMap<Long, PlannedState> states = new LinkedHashMap<>();
        planReferenceVolume(states, footprint, materials);

        List<AutogenPlan.Operation> railFinalizations = new ArrayList<>();
        placeTracks(states, geometry.route, materials.reinforcedTrack,
                railFinalizations);
        int[] catenaryLogicalPositions = catenaryLogicalPositions(geometry.route);
        Set<Integer> supports = findSupportIndices(
                geometry, footprint, catenaryLogicalPositions);
        Set<Integer> droppers = findDropperIndices(
                geometry.route, supports, catenaryLogicalPositions);
        placeCatenary(states, geometry, supports, droppers, materials);

        int operationCount = states.size() + railFinalizations.size();
        if (operationCount > settings.maxChangedBlocks) {
            throw new AutogenBuildException(
                    "预计修改方块数超过配置上限 " + settings.maxChangedBlocks);
        }
        List<PlannedState> ordered = new ArrayList<>(states.values());
        Collections.sort(ordered, Comparator.comparingInt(value -> value.priority));
        List<AutogenPlan.Operation> operations = new ArrayList<>(operationCount);
        for (PlannedState state : ordered) {
            operations.add(new AutogenPlan.Operation(state.pos, state.state));
        }
        operations.addAll(railFinalizations);

        return new AutogenPlan(world.provider.getDimension(), operations,
                new ArrayList<>(geometry.route), createPreviewFrames(geometry),
                geometry.length, geometry.minimumRadius, geometry.maximumGrade,
                templateId, displayName, AutogenTemplateKind.BRIDGE);
    }

    static void validateSelection(WorldServer world,
                                  AutogenSelection.Selection selection)
            throws AutogenBuildException {
        if (selection == null || !selection.isComplete()) {
            throw new AutogenBuildException("请先用自动生成魔杖选择两个标记方块");
        }
        if (selection.start.dimension != selection.end.dimension
                || selection.start.dimension != world.provider.getDimension()) {
            throw new AutogenBuildException("两个标记必须与玩家位于同一维度");
        }
        if (!isMarker(world, selection.start.pos)
                || !isMarker(world, selection.end.pos)) {
            throw new AutogenBuildException("已选择的标记方块不存在或已被移动");
        }
        if (world.getBlockState(selection.start.pos).getValue(BlockAutogenMarker.FACING)
                != selection.start.facing
                || world.getBlockState(selection.end.pos).getValue(BlockAutogenMarker.FACING)
                != selection.end.facing) {
            throw new AutogenBuildException("标记在选择后被旋转，请用魔杖重新选择两个标记");
        }
    }

    static void validateBuildHeight(WorldServer world, List<BlockPos> route)
            throws AutogenBuildException {
        for (BlockPos center : route) {
            if (center.getY() - 1 < 0
                    || center.getY() + CATENARY_Y >= world.getHeight()) {
                throw new AutogenBuildException(
                        "桥梁结构将超出世界高度范围，请调整标记高度");
            }
        }
    }

    /**
     * Rebuild every voxel in the sampled seven-high section. On a straight route this is exactly:
     * air/concrete/trackbed/fence/contact wire for the five by seven by nine reference volume.
     */
    private static void planReferenceVolume(
            Map<Long, PlannedState> states, Footprint footprint,
            Materials materials) {
        for (BridgeColumn column : footprint.columns.values()) {
            BlockPos deck = column.deck;
            put(states, deck.down(), Blocks.AIR.getDefaultState(), 10);
            for (int y = TRACK_Y; y <= CATENARY_Y; y++) {
                put(states, deck.up(y), Blocks.AIR.getDefaultState(), 10);
            }

            if (column.distance <= INNER_HALF_WIDTH) {
                put(states, deck.down(), materials.concrete, 20);
            }
            if (column.distance == 0) {
                put(states, deck, materials.centerTrackbed, 30);
            } else if (column.distance == INNER_HALF_WIDTH) {
                put(states, deck, column.slopeTrackbed
                        ? materials.centerTrackbed : materials.sideTrackbed, 30);
            } else {
                put(states, deck, materials.concrete, 30);
                put(states, deck.up(TRACK_Y), materials.fence, 40);
            }
        }
    }

    static void placeTracks(
            Map<Long, PlannedState> states, List<BlockPos> route,
            Block reinforcedTrack,
            List<AutogenPlan.Operation> railFinalizations) {
        for (int i = 0; i < route.size(); i++) {
            BlockPos railPos = route.get(i).up(TRACK_Y);
            IBlockState rail = reinforcedTrack.getStateFromMeta(railMeta(route, i));
            put(states, railPos, rail, 50);
            // Railcraft reshapes flex track when first placed. Reapply after every rail exists.
            railFinalizations.add(new AutogenPlan.Operation(railPos, rail, false));
        }
    }

    private static void placeCatenary(
            Map<Long, PlannedState> states, RouteGeometry geometry,
            Set<Integer> supports, Set<Integer> droppers,
            Materials materials)
            throws AutogenBuildException {
        for (int i = 0; i < geometry.route.size(); i++) {
            BlockPos center = geometry.route.get(i);
            IBlockState wire = catenaryState(
                    geometry.route, i, geometry.sectionFacings.get(i),
                    supports, droppers, materials);
            put(states, center.up(CATENARY_Y), wire, 50);

            if (!supports.contains(i)) {
                continue;
            }
            EnumFacing poleSide = geometry.sectionFacings.get(i).rotateYCCW();
            BlockPos poleBase = center.offset(poleSide, HALF_WIDTH);
            for (int y = TRACK_Y; y <= CATENARY_Y; y++) {
                put(states, poleBase.up(y),
                        materials.catenaryPole.getDefaultState(), 60);
            }
            BlockPos support = center.offset(poleSide, INNER_HALF_WIDTH)
                    .up(CATENARY_Y);
            put(states, support,
                    nebulaFacingState(materials.catenarySupport,
                            poleSide.getOpposite()), 61);
        }
    }

    static IBlockState catenaryState(
            List<BlockPos> route, int index, EnumFacing curveFacing,
            Set<Integer> supports, Set<Integer> droppers,
            Materials materials) throws AutogenBuildException {
        Block block;
        if (isSlopeRailCell(route, index)) {
            block = materials.catenarySlope;
            if (gradeDirection(route, index) < 0) {
                curveFacing = curveFacing.getOpposite();
            }
        } else if (turn(route, index) != 0) {
            block = materials.catenaryDiagonal;
            curveFacing = cornerFacing(route, index);
        } else if (supports.contains(index)) {
            // A moved support may land on a dropper phase. The support assembly wins.
            block = materials.catenaryWire;
        } else if (droppers.contains(index)) {
            block = materials.catenaryDropper;
        } else {
            block = materials.catenaryWire;
        }
        return nebulaFacingState(block, curveFacing);
    }

    /**
     * Place support assemblies in route order. After placing one assembly, skip the next eight
     * logical contact-wire cells and then use the first compatible cell at or beyond that target.
     * Searching only forwards prevents an unavailable curve span from moving a support back next
     * to the preceding pole; the actual selected cell becomes the origin for the next interval.
     */
    private static Set<Integer> findSupportIndices(
            RouteGeometry geometry, Footprint footprint,
            int[] logicalPositions) {
        return scheduleSupportIndices(
                geometry.route.size(), logicalPositions,
                index -> isSupportCompatible(geometry, footprint, index));
    }

    /** Shared bridge support scheduler; template-specific code supplies the footprint check. */
    static Set<Integer> scheduleSupportIndices(
            int routeSize, int[] logicalPositions,
            IntPredicate compatible) {
        Set<Integer> result = new HashSet<>();
        int nextLogicalPosition = Integer.MIN_VALUE;
        for (int i = 0; i < routeSize; i++) {
            if (logicalPositions[i] < nextLogicalPosition
                    || !compatible.test(i)) {
                continue;
            }
            result.add(i);
            nextLogicalPosition = logicalPositions[i] + REPEAT_LENGTH;
        }
        return result;
    }

    /**
     * Plan at most two droppers inside each adjacent pair of support assemblies. Their ideal
     * positions are the third and sixth logical cells after the first support. If either ideal
     * lands on diagonal wire, use the nearest level straight cell without crossing a support.
     * The physical cells directly beside either support remain plain contact wire.
     */
    static Set<Integer> findDropperIndices(
            List<BlockPos> route, Set<Integer> supports,
            int[] logicalPositions) {
        List<Integer> orderedSupports = new ArrayList<>(supports);
        Collections.sort(orderedSupports);
        Set<Integer> result = new HashSet<>();
        for (int supportIndex = 0;
             supportIndex + 1 < orderedSupports.size(); supportIndex++) {
            int firstSupport = orderedSupports.get(supportIndex);
            int secondSupport = orderedSupports.get(supportIndex + 1);
            int firstLogicalPosition = logicalPositions[firstSupport];
            int secondLogicalPosition = logicalPositions[secondSupport];
            int[] idealPositions = {
                    firstLogicalPosition + 3,
                    firstLogicalPosition + 6
            };
            for (int idealPosition : idealPositions) {
                if (idealPosition >= secondLogicalPosition) {
                    continue;
                }
                int selected = nearestDropperCandidate(
                        route, firstSupport, secondSupport, idealPosition,
                        logicalPositions, result);
                if (selected >= 0) {
                    result.add(selected);
                }
            }
        }
        return result;
    }

    private static int nearestDropperCandidate(
            List<BlockPos> route, int firstSupport, int secondSupport,
            int idealPosition, int[] logicalPositions,
            Set<Integer> selectedDroppers) {
        int selected = -1;
        int selectedDistance = Integer.MAX_VALUE;
        // Exclude the one physical route cell immediately beside either support.
        for (int index = firstSupport + 2; index <= secondSupport - 2; index++) {
            if (selectedDroppers.contains(index)
                    || turn(route, index) != 0
                    || isSlopeRailCell(route, index)) {
                continue;
            }
            int distance = Math.abs(logicalPositions[index] - idealPosition);
            if (distance < selectedDistance) {
                selected = index;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    /** Count every adjacent pair of diagonal contact-wire cells as one route position. */
    static int[] catenaryLogicalPositions(List<BlockPos> route) {
        int[] result = new int[route.size()];
        int logicalPosition = 0;
        int index = 0;
        while (index < route.size()) {
            result[index] = logicalPosition;
            if (turn(route, index) != 0
                    && index + 1 < route.size()
                    && turn(route, index + 1) != 0) {
                result[index + 1] = logicalPosition;
                index += 2;
            } else {
                index++;
            }
            logicalPosition++;
        }
        return result;
    }

    private static boolean isSupportCompatible(
            RouteGeometry geometry, Footprint footprint, int index) {
        if (turn(geometry.route, index) != 0
                || isSlopeRailCell(geometry.route, index)) {
            return false;
        }
        BlockPos center = geometry.route.get(index);
        EnumFacing poleSide = geometry.sectionFacings.get(index).rotateYCCW();
        BridgeColumn inner = footprint.columns.get(xzKey(
                center.getX() + poleSide.getFrontOffsetX(),
                center.getZ() + poleSide.getFrontOffsetZ()));
        BridgeColumn outer = footprint.columns.get(xzKey(
                center.getX() + poleSide.getFrontOffsetX() * HALF_WIDTH,
                center.getZ() + poleSide.getFrontOffsetZ() * HALF_WIDTH));
        boolean concretePoleBase = outer != null
                && outer.distance == HALF_WIDTH;
        boolean stoneSlabPoleBase = outer != null
                && outer.distance == INNER_HALF_WIDTH
                && !outer.slopeTrackbed;
        return inner != null && outer != null
                && inner.distance == INNER_HALF_WIDTH
                && (concretePoleBase || stoneSlabPoleBase)
                && inner.deck.getY() == center.getY()
                && outer.deck.getY() == center.getY();
    }

    /**
     * Build the three semantic bridge bands independently. A plain distance field leaves each
     * offset lane touching only diagonally whenever the raster curve changes axis. Match
     * tunnel_metro_1 by inserting the unique elbow outside the inner band, first for the 44:0
     * trackbed and then for the concrete/fence edge. The lower beam follows the completed
     * trackbed, so every widened slab elbow is supported as well.
     */
    private static Footprint createFootprint(
            List<BlockPos> route, GradeLayout gradeLayout) {
        LinkedHashMap<Long, Integer> distances = new LinkedHashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (BlockPos center : route) {
            long key = xzKey(center.getX(), center.getZ());
            if (!distances.containsKey(key)) {
                distances.put(key, 0);
                queue.addLast(key);
            }
        }
        while (!queue.isEmpty()) {
            long key = queue.removeFirst();
            int distance = distances.get(key);
            if (distance >= HALF_WIDTH) {
                continue;
            }
            int x = xFromKey(key);
            int z = zFromKey(key);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                long next = xzKey(x + direction.getFrontOffsetX(),
                        z + direction.getFrontOffsetZ());
                if (distances.containsKey(next)) {
                    continue;
                }
                distances.put(next, distance + 1);
                queue.addLast(next);
            }
        }

        Set<Long> routeCells = new HashSet<>();
        Set<Long> centerCells = new HashSet<>();
        Set<Long> sideCells = new HashSet<>();
        for (Map.Entry<Long, Integer> entry : distances.entrySet()) {
            int x = xFromKey(entry.getKey());
            int z = zFromKey(entry.getKey());
            if (isBeyondOpenRouteEnd(x, z, route)) {
                continue;
            }
            if (entry.getValue() == 0) {
                routeCells.add(entry.getKey());
                centerCells.add(entry.getKey());
            } else if (entry.getValue() == INNER_HALF_WIDTH) {
                sideCells.add(entry.getKey());
            }
        }
        // As in tunnel_metro_1, the analytic slope normal owns a solid three-wide 43:8
        // trackbed. Its two side cells can be diagonal to the raster route on a curve, so merge
        // them explicitly instead of relying on the Manhattan distance-one band.
        for (long slopeTrackbed : gradeLayout.slopeTrackbedCells) {
            int x = xFromKey(slopeTrackbed);
            int z = zFromKey(slopeTrackbed);
            if (!isBeyondOpenRouteEnd(x, z, route)) {
                centerCells.add(slopeTrackbed);
            }
        }
        sideCells.removeAll(centerCells);
        connectBandCardinally(sideCells, centerCells, route);

        // Match tunnel_metro_1's semantic TrackbedLayout: the two off-route cells in a slope's
        // authoritative 43:8 row still belong to the left/right trackbed curves. Treating every
        // 43:8 cell as center-line obstruction can split those curves after the analytic normal is
        // rasterized in a different phase. Reconnect the combined 44:0 + slope-side 43:8 bands,
        // with only real rail-route cells acting as the inner obstacle, then materialize newly
        // selected outer elbows as 44:0.
        Set<Long> sideCurveCells = new HashSet<>(sideCells);
        for (long centerCell : centerCells) {
            if (!routeCells.contains(centerCell)) {
                sideCurveCells.add(centerCell);
            }
        }
        connectBandCardinally(sideCurveCells, routeCells, route);
        sideCurveCells.removeAll(centerCells);
        sideCells.addAll(sideCurveCells);

        Set<Long> trackbedCells = new HashSet<>(centerCells);
        trackbedCells.addAll(sideCells);
        Set<Long> edgeCells = new HashSet<>();
        for (long key : trackbedCells) {
            int x = xFromKey(key);
            int z = zFromKey(key);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                int edgeX = x + direction.getFrontOffsetX();
                int edgeZ = z + direction.getFrontOffsetZ();
                long edge = xzKey(edgeX, edgeZ);
                if (!trackbedCells.contains(edge)
                        && !isBeyondOpenRouteEnd(edgeX, edgeZ, route)) {
                    edgeCells.add(edge);
                }
            }
        }
        connectBandCardinally(edgeCells, trackbedCells, route);

        List<Long> orderedCells = new ArrayList<>(trackbedCells);
        orderedCells.addAll(edgeCells);
        Collections.sort(orderedCells);
        LinkedHashMap<Long, BridgeColumn> columns = new LinkedHashMap<>();
        for (long key : orderedCells) {
            int distance = centerCells.contains(key)
                    ? 0 : sideCells.contains(key) ? INNER_HALF_WIDTH : HALF_WIDTH;
            BlockPos deck = gradeLayout.floorAt(xFromKey(key), zFromKey(key));
            columns.put(key, new BridgeColumn(
                    deck, distance, distance == INNER_HALF_WIDTH
                    && gradeLayout.isSlopeSection(deck)));
        }
        return new Footprint(columns);
    }

    /**
     * Join diagonal fragments of one offset band with the only cardinal elbow outside its inner
     * band. The other elbow belongs to the inner material and must remain untouched.
     */
    static void connectBandCardinally(
            Set<Long> bandCells, Set<Long> innerCells, List<BlockPos> route) {
        Map<Long, Integer> componentIds = cardinalComponentIds(bandCells);
        int componentCount = 0;
        for (int component : componentIds.values()) {
            componentCount = Math.max(componentCount, component + 1);
        }
        if (componentCount <= 1) {
            return;
        }
        int[] parents = new int[componentCount];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = i;
        }

        List<BandBridge> candidates = new ArrayList<>();
        List<Long> orderedCells = new ArrayList<>(bandCells);
        Collections.sort(orderedCells);
        for (long first : orderedCells) {
            int firstX = xFromKey(first);
            int firstZ = zFromKey(first);
            int firstComponent = componentIds.get(first);
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    long second = xzKey(firstX + dx, firstZ + dz);
                    Integer secondComponent = componentIds.get(second);
                    if (secondComponent == null
                            || firstComponent == secondComponent) {
                        continue;
                    }
                    long xThenZ = xzKey(firstX + dx, firstZ);
                    long zThenX = xzKey(firstX, firstZ + dz);
                    boolean firstElbowInner = innerCells.contains(xThenZ);
                    boolean secondElbowInner = innerCells.contains(zThenX);
                    if (firstElbowInner == secondElbowInner) {
                        continue;
                    }
                    long bridge = firstElbowInner ? zThenX : xThenZ;
                    int bridgeX = xFromKey(bridge);
                    int bridgeZ = zFromKey(bridge);
                    if (innerCells.contains(bridge)
                            || bandCells.contains(bridge)
                            || isBeyondOpenRouteEnd(bridgeX, bridgeZ, route)) {
                        continue;
                    }
                    candidates.add(new BandBridge(
                            Math.min(firstComponent, secondComponent),
                            Math.max(firstComponent, secondComponent), bridge));
                }
            }
        }

        Collections.sort(candidates, Comparator.comparingLong(value -> value.pos));
        for (BandBridge candidate : candidates) {
            int firstRoot = componentRoot(parents, candidate.firstComponent);
            int secondRoot = componentRoot(parents, candidate.secondComponent);
            if (firstRoot == secondRoot) {
                continue;
            }
            parents[firstRoot] = secondRoot;
            bandCells.add(candidate.pos);
        }
    }

    private static Map<Long, Integer> cardinalComponentIds(Set<Long> cells) {
        Map<Long, Integer> result = new HashMap<>();
        Set<Long> remaining = new HashSet<>(cells);
        int component = 0;
        while (!remaining.isEmpty()) {
            long start = remaining.iterator().next();
            remaining.remove(start);
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            result.put(start, component);
            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                int x = xFromKey(current);
                int z = zFromKey(current);
                for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                    long next = xzKey(x + direction.getFrontOffsetX(),
                            z + direction.getFrontOffsetZ());
                    if (remaining.remove(next)) {
                        result.put(next, component);
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

    static boolean isBeyondOpenRouteEnd(
            int x, int z, List<BlockPos> route) {
        if (route.size() < 2) {
            return false;
        }
        BlockPos start = route.get(0);
        EnumFacing startForward = horizontalDirection(start, route.get(1));
        if (startForward != null && horizontalDot(start, x, z, startForward) < 0) {
            return true;
        }
        BlockPos end = route.get(route.size() - 1);
        EnumFacing endForward = horizontalDirection(
                route.get(route.size() - 2), end);
        return endForward != null
                && horizontalDot(end, x, z, endForward) > 0;
    }

    private static int horizontalDot(
            BlockPos origin, int x, int z, EnumFacing facing) {
        return (x - origin.getX()) * facing.getFrontOffsetX()
                + (z - origin.getZ()) * facing.getFrontOffsetZ();
    }

    static long horizontalDistanceToRoute(
            BlockPos pos, List<BlockPos> route) {
        long best = Long.MAX_VALUE;
        for (BlockPos center : route) {
            long dx = pos.getX() - center.getX();
            long dz = pos.getZ() - center.getZ();
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static List<AutogenPlan.PreviewFrame> createPreviewFrames(
            RouteGeometry geometry) {
        return createPreviewFrames(geometry, HALF_WIDTH);
    }

    static List<AutogenPlan.PreviewFrame> createPreviewFrames(
            RouteGeometry geometry, int halfWidth) {
        List<AutogenPlan.PreviewFrame> frames = new ArrayList<>(geometry.route.size());
        for (int i = 0; i < geometry.route.size(); i++) {
            BlockPos center = geometry.route.get(i);
            EnumFacing forward = geometry.sectionFacings.get(i);
            EnumFacing left = forward.rotateYCCW();
            double sectionX = center.getX() + 0.5D;
            double sectionZ = center.getZ() + 0.5D;
            if (i == 0) {
                sectionX -= forward.getFrontOffsetX() * 0.5D;
                sectionZ -= forward.getFrontOffsetZ() * 0.5D;
            } else if (i == geometry.route.size() - 1) {
                sectionX += forward.getFrontOffsetX() * 0.5D;
                sectionZ += forward.getFrontOffsetZ() * 0.5D;
            }
            double leftX = sectionX
                    + left.getFrontOffsetX() * (halfWidth + 0.5D);
            double leftZ = sectionZ
                    + left.getFrontOffsetZ() * (halfWidth + 0.5D);
            double rightX = sectionX
                    - left.getFrontOffsetX() * (halfWidth + 0.5D);
            double rightZ = sectionZ
                    - left.getFrontOffsetZ() * (halfWidth + 0.5D);
            boolean ring = i == 0 || i == geometry.route.size() - 1
                    || i % REPEAT_LENGTH == 0
                    || previewFrameChanged(geometry, i);
            frames.add(new AutogenPlan.PreviewFrame(
                    new Vec3d(leftX, center.getY() - 1, leftZ),
                    new Vec3d(leftX, center.getY() + CATENARY_Y + 1, leftZ),
                    new Vec3d(rightX, center.getY() + CATENARY_Y + 1, rightZ),
                    new Vec3d(rightX, center.getY() - 1, rightZ), ring));
        }
        return frames;
    }

    private static boolean previewFrameChanged(RouteGeometry geometry, int index) {
        if (index <= 0) {
            return true;
        }
        if (geometry.route.get(index - 1).getY()
                != geometry.route.get(index).getY()
                || geometry.sectionFacings.get(index - 1).getAxis()
                != geometry.sectionFacings.get(index).getAxis()) {
            return true;
        }
        return index + 1 < geometry.route.size()
                && turn(geometry.route, index) != 0;
    }

    private static int railMeta(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        BlockPos previous = index > 0 ? route.get(index - 1) : null;
        BlockPos next = index + 1 < route.size() ? route.get(index + 1) : null;
        BlockPos higher = null;
        if (next != null && next.getY() > current.getY()) {
            higher = next;
        }
        if (previous != null && previous.getY() > current.getY()) {
            higher = previous;
        }
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
        EnumFacing a = previous == null
                ? null : horizontalDirection(current, previous);
        EnumFacing b = next == null
                ? null : horizontalDirection(current, next);
        if (a == null) {
            a = b == null ? EnumFacing.NORTH : b.getOpposite();
        }
        if (b == null) {
            b = a.getOpposite();
        }
        Set<EnumFacing> pair = new HashSet<>();
        pair.add(a);
        pair.add(b);
        if (pair.contains(EnumFacing.NORTH) && pair.contains(EnumFacing.SOUTH)) {
            return 0;
        }
        if (pair.contains(EnumFacing.EAST) && pair.contains(EnumFacing.WEST)) {
            return 1;
        }
        if (pair.contains(EnumFacing.SOUTH) && pair.contains(EnumFacing.EAST)) {
            return 6;
        }
        if (pair.contains(EnumFacing.SOUTH) && pair.contains(EnumFacing.WEST)) {
            return 7;
        }
        if (pair.contains(EnumFacing.NORTH) && pair.contains(EnumFacing.WEST)) {
            return 8;
        }
        if (pair.contains(EnumFacing.NORTH) && pair.contains(EnumFacing.EAST)) {
            return 9;
        }
        return routeFacing(route, index).getAxis() == EnumFacing.Axis.X ? 1 : 0;
    }

    private static EnumFacing cornerFacing(List<BlockPos> route, int index)
            throws AutogenBuildException {
        if (index <= 0 || index + 1 >= route.size()) {
            throw new AutogenBuildException("路线端点不能使用斜线接触网模型");
        }
        BlockPos current = route.get(index);
        EnumFacing previous = horizontalDirection(current, route.get(index - 1));
        EnumFacing next = horizontalDirection(current, route.get(index + 1));
        if (previous == null || next == null
                || previous.getAxis() == next.getAxis()) {
            throw new AutogenBuildException("无法匹配斜线接触网的实际模型方向");
        }
        for (EnumFacing candidate : EnumFacing.HORIZONTALS) {
            if (candidate == previous && candidate.rotateYCCW() == next
                    || candidate == next && candidate.rotateYCCW() == previous) {
                return candidate;
            }
        }
        throw new AutogenBuildException("无法匹配斜线接触网的实际模型方向");
    }

    private static EnumFacing routeFacing(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        if (index + 1 < route.size()) {
            EnumFacing facing = horizontalDirection(current, route.get(index + 1));
            if (facing != null) {
                return facing;
            }
        }
        if (index > 0) {
            EnumFacing facing = horizontalDirection(route.get(index - 1), current);
            if (facing != null) {
                return facing;
            }
        }
        return EnumFacing.NORTH;
    }

    private static EnumFacing horizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.signum(to.getX() - from.getX());
        int dz = Integer.signum(to.getZ() - from.getZ());
        if (dx > 0) {
            return EnumFacing.EAST;
        }
        if (dx < 0) {
            return EnumFacing.WEST;
        }
        if (dz > 0) {
            return EnumFacing.SOUTH;
        }
        if (dz < 0) {
            return EnumFacing.NORTH;
        }
        return null;
    }

    static int turn(List<BlockPos> route, int index) {
        if (index <= 0 || index + 1 >= route.size()) {
            return 0;
        }
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

    static boolean isSlopeRailCell(List<BlockPos> route, int index) {
        BlockPos current = route.get(index);
        return index > 0 && route.get(index - 1).getY() > current.getY()
                || index + 1 < route.size()
                && route.get(index + 1).getY() > current.getY();
    }

    private static boolean isMarker(WorldServer world, BlockPos pos) {
        ResourceLocation name = world.getBlockState(pos).getBlock().getRegistryName();
        return name != null && name.toString().equals("nebulaecraft:autogen_marker");
    }

    private static IBlockState resolveState(String descriptor)
            throws AutogenBuildException {
        String[] parts = descriptor.split("@", 2);
        Block block = resolveBlock(descriptor);
        int meta = parts.length == 2 ? parseMeta(parts[1], descriptor) : 0;
        return block.getStateFromMeta(meta);
    }

    private static Block resolveBlock(String descriptor)
            throws AutogenBuildException {
        String id = descriptor.split("@", 2)[0];
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR && !id.equals("minecraft:air")) {
            throw new AutogenBuildException("找不到桥梁模板所需方块: " + id);
        }
        return block;
    }

    private static int parseMeta(String value, String descriptor)
            throws AutogenBuildException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new AutogenBuildException("无效方块 metadata: " + descriptor);
        }
    }

    static IBlockState nebulaFacingState(Block block, EnumFacing facing)
            throws AutogenBuildException {
        switch (facing) {
            case NORTH:
                return block.getStateFromMeta(0);
            case SOUTH:
                return block.getStateFromMeta(1);
            case WEST:
                return block.getStateFromMeta(2);
            case EAST:
                return block.getStateFromMeta(3);
            default:
                throw new AutogenBuildException("桥梁旋转方块需要水平方向");
        }
    }

    static void put(Map<Long, PlannedState> states, BlockPos pos,
                            IBlockState state, int priority) {
        long key = pos.toLong();
        PlannedState current = states.get(key);
        if (current == null || priority >= current.priority) {
            states.put(key, new PlannedState(pos, state, priority));
        }
    }

    static long xzKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    static int xFromKey(long key) {
        return (int) (key >> 32);
    }

    static int zFromKey(long key) {
        return (int) key;
    }

    /** Assign the full five-wide slope section to the low rail cell. */
    private static final class GradeLayout {
        final List<BlockPos> route;
        final List<RouteVector> tangents;
        final List<GradeTransition> transitions = new ArrayList<>();
        final Map<Long, Integer> slopeFloors = new HashMap<>();
        final Set<Long> slopeTrackbedCells = new HashSet<>();

        GradeLayout(List<BlockPos> route, List<RouteVector> tangents) {
            this.route = route;
            this.tangents = tangents;
            for (int i = 1; i < route.size(); i++) {
                int deltaY = route.get(i).getY() - route.get(i - 1).getY();
                if (deltaY == 0) {
                    continue;
                }
                int lowIndex = deltaY > 0 ? i - 1 : i;
                transitions.add(new GradeTransition(
                        route.get(lowIndex), tangents.get(lowIndex)));
            }
            // Keep this identical to tunnel_metro_1: every lateral index is projected directly
            // onto the Chebyshev-scaled analytic normal. Rasterizing one endpoint-to-endpoint line
            // gives its minor-axis step to only one half of shallow sections and shifts one side of
            // the three-wide 43:8 slope bed forward or backward by a block.
            for (GradeTransition transition : transitions) {
                for (int lateral = -HALF_WIDTH; lateral <= HALF_WIDTH; lateral++) {
                    BlockPos cell = transition.normalOffset(lateral);
                    long key = xzKey(cell.getX(), cell.getZ());
                    slopeFloors.put(key, transition.lowCenter.getY());
                    if (Math.abs(lateral) <= INNER_HALF_WIDTH) {
                        slopeTrackbedCells.add(key);
                    }
                }
            }
        }

        BlockPos floorAt(int x, int z) {
            Integer slopeY = slopeFloors.get(xzKey(x, z));
            if (slopeY != null) {
                return new BlockPos(x, slopeY, z);
            }
            return new BlockPos(x, route.get(sectionIndex(x, z)).getY(), z);
        }

        /** Use tunnel_metro_1's tangent-normal section ownership for non-slope bridge cells. */
        private int sectionIndex(int x, int z) {
            int nearest = closestHorizontalRouteIndex(x, z);
            int first = Math.max(0, nearest - LOCAL_TRANSITION_PATH_LIMIT);
            int last = Math.min(route.size() - 1,
                    nearest + LOCAL_TRANSITION_PATH_LIMIT);
            int selected = nearest;
            double bestLongitudinal = Double.POSITIVE_INFINITY;
            long bestDistance = Long.MAX_VALUE;
            for (int i = first; i <= last; i++) {
                BlockPos center = route.get(i);
                long dx = x - center.getX();
                long dz = z - center.getZ();
                RouteVector tangent = tangents.get(i);
                double tangentLength = Math.sqrt(
                        tangent.x * tangent.x + tangent.z * tangent.z);
                if (tangentLength <= 1.0E-8) {
                    continue;
                }
                double longitudinal = Math.abs(
                        (dx * tangent.x + dz * tangent.z) / tangentLength);
                long distance = dx * dx + dz * dz;
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

        private int closestHorizontalRouteIndex(int x, int z) {
            int selected = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < route.size(); i++) {
                BlockPos center = route.get(i);
                long dx = x - (long) center.getX();
                long dz = z - (long) center.getZ();
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    selected = i;
                    bestDistance = distance;
                }
            }
            return selected;
        }

        boolean isSlopeSection(BlockPos pos) {
            Integer slopeY = slopeFloors.get(xzKey(pos.getX(), pos.getZ()));
            return slopeY != null && slopeY == pos.getY();
        }
    }

    /** The same Chebyshev-scaled analytic slope normal used by tunnel_metro_1. */
    private static final class GradeTransition {
        final BlockPos lowCenter;
        final double normalX;
        final double normalZ;

        GradeTransition(BlockPos lowCenter, RouteVector tangent) {
            this.lowCenter = lowCenter;
            double rasterScale = Math.max(Math.abs(tangent.x), Math.abs(tangent.z));
            normalX = -tangent.z / rasterScale;
            normalZ = tangent.x / rasterScale;
        }

        BlockPos normalOffset(int lateral) {
            return new BlockPos(
                    Math.round(lowCenter.getX() + normalX * lateral),
                    lowCenter.getY(),
                    Math.round(lowCenter.getZ() + normalZ * lateral));
        }
    }

    static final class Materials {
        final IBlockState concrete;
        final IBlockState centerTrackbed;
        final IBlockState sideTrackbed;
        final IBlockState fence;
        final Block reinforcedTrack;
        final Block catenaryWire;
        final Block catenaryDropper;
        final Block catenaryDiagonal;
        final Block catenarySlope;
        final Block catenaryPole;
        final Block catenarySupport;

        Materials() throws AutogenBuildException {
            concrete = resolveState("railcraft:reinforced_concrete@8");
            centerTrackbed = resolveState("minecraft:double_stone_slab@8");
            sideTrackbed = resolveState("minecraft:stone_slab@0");
            fence = resolveState("nebulaecraft:metro_fence@0");
            reinforcedTrack = resolveBlock("railcraft:track_flex_reinforced@0");
            catenaryWire = resolveBlock("nebulaecraft:catenary_wire@0");
            catenaryDropper = resolveBlock("nebulaecraft:catenary_wire_dropper@0");
            catenaryDiagonal = resolveBlock("nebulaecraft:catenary_wire_diagonal@0");
            catenarySlope = resolveBlock("nebulaecraft:catenary_wire_slope@0");
            catenaryPole = resolveBlock(
                    "nebulaecraft:catenary_wire_pole_regular@0");
            catenarySupport = resolveBlock("nebulaecraft:catenary_wire_support@0");
        }
    }

    private static final class Footprint {
        final Map<Long, BridgeColumn> columns;

        Footprint(Map<Long, BridgeColumn> columns) {
            this.columns = columns;
        }
    }

    private static final class BridgeColumn {
        final BlockPos deck;
        final int distance;
        final boolean slopeTrackbed;

        BridgeColumn(BlockPos deck, int distance, boolean slopeTrackbed) {
            this.deck = deck;
            this.distance = distance;
            this.slopeTrackbed = slopeTrackbed;
        }
    }

    private static final class BandBridge {
        final int firstComponent;
        final int secondComponent;
        final long pos;

        BandBridge(int firstComponent, int secondComponent, long pos) {
            this.firstComponent = firstComponent;
            this.secondComponent = secondComponent;
            this.pos = pos;
        }
    }

    static final class PlannedState {
        final BlockPos pos;
        final IBlockState state;
        final int priority;

        PlannedState(BlockPos pos, IBlockState state, int priority) {
            this.pos = pos;
            this.state = state;
            this.priority = priority;
        }
    }
}
