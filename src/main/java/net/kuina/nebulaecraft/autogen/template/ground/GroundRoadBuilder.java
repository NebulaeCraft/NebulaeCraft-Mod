package net.kuina.nebulaecraft.autogen.template.ground;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenBuildOrder;
import net.kuina.nebulaecraft.autogen.AutogenConfig;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.route.RouteGeometry;
import net.kuina.nebulaecraft.autogen.route.RoutePlanner;
import net.kuina.nebulaecraft.autogen.route.RouteVector;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.kuina.nebulaecraft.block.BlockAutogenMarker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
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
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sweeps the reference road cross-sections with shared curved markings and half-block grades.
 * Ground roads need no railway materials; bridge variants use the reference concrete and fences.
 * No save data or client classes are needed at runtime.
 */
final class GroundRoadBuilder {
    private static final double MINIMUM_RADIUS = 12.0;
    private static final double MAXIMUM_GRADE = 0.1;
    private static final int SECTION_SEARCH_RADIUS = 6;

    private GroundRoadBuilder() {
    }

    static AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                             String templateId, String displayName,
                             GroundRoadProfile profile, int dashLength, boolean bridge)
            throws AutogenBuildException {
        if (dashLength != 2 && dashLength != 3) {
            throw new AutogenBuildException("虚线实空组合只能为 2+2 或 3+3");
        }
        validateSelection(world, selection);
        RouteGeometry geometry = RoutePlanner.plan(selection, MINIMUM_RADIUS, MAXIMUM_GRADE);
        AutogenConfig.Settings settings = AutogenConfig.get();
        if (geometry.length > settings.maxPathLength) {
            throw new AutogenBuildException("路线长度超过配置上限 " + settings.maxPathLength);
        }

        Footprint footprint = createFootprint(geometry, profile);
        if (footprint.cells.size() > settings.maxChangedBlocks) {
            throw new AutogenBuildException("预计修改方块数超过配置上限 "
                    + settings.maxChangedBlocks);
        }
        Materials materials = new Materials(profile);
        SurfaceLayout layout = new SurfaceLayout(geometry);
        Map<Long, IBlockState> states = new LinkedHashMap<>();
        List<Long> orderedCells = new ArrayList<>(footprint.cells);
        Collections.sort(orderedCells);
        for (long cell : orderedCells) {
            states.put(cell, materials.asphalt);
        }
        if (materials.median != null) {
            for (BlockPos pos : geometry.route) {
                states.put(key(pos), materials.median.concrete);
            }
        }
        int[] logicalPositions = logicalPositions(geometry.route);
        for (GroundRoadProfile.Line line : profile.lines) {
            List<BlockPos> path = footprint.lines.get(line.leftOffset);
            placeLine(states, path,
                    materials.straight.get(line.marking), materials.corners.get(line.marking),
                    line.dashed ? alignedLogicalPositions(path, layout, logicalPositions) : null,
                    dashLength);
        }

        List<AutogenPlan.Operation> operations = new ArrayList<>(states.size());
        for (Map.Entry<Long, IBlockState> entry : states.entrySet()) {
            RoadSurface surface = layout.surfaceAt(entry.getKey());
            if (surface.base.getY() < 0 || surface.top() > world.getHeight()) {
                throw new AutogenBuildException("道路将超出世界高度范围，请调整标记高度");
            }
            if (surface.slab == null || !surface.slab.equals(surface.base)) {
                operations.add(new AutogenPlan.Operation(surface.base, entry.getValue()));
            }
            if (surface.slab != null) {
                operations.add(new AutogenPlan.Operation(
                        surface.slab, materials.slabState(entry.getValue())));
            }
        }
        if (operations.size() > settings.maxChangedBlocks) {
            throw new AutogenBuildException("预计修改方块数超过配置上限 "
                    + settings.maxChangedBlocks);
        }
        AutogenBuildOrder buildOrder = new AutogenBuildOrder(AutogenBuildOrder.Stage.SURFACE);
        if (bridge) {
            addBridgeStructure(operations, footprint, geometry, layout, buildOrder);
        }
        if (materials.median != null) {
            addMedianStructure(operations, geometry, layout, materials.median, buildOrder);
        }
        if (operations.size() > settings.maxChangedBlocks) {
            throw new AutogenBuildException("预计修改方块数超过配置上限 "
                    + settings.maxChangedBlocks);
        }
        for (AutogenPlan.Operation operation : operations) {
            if (operation.pos.getY() < 0 || operation.pos.getY() >= world.getHeight()) {
                throw new AutogenBuildException("道路将超出世界高度范围，请调整标记高度");
            }
        }
        buildOrder.sort(operations, geometry.route);
        return new AutogenPlan(world.provider.getDimension(), operations, geometry.route,
                createPreviewFrames(geometry, logicalPositions, 2 * dashLength,
                        profile.halfWidth, layout, bridge, profile.median),
                geometry.length, geometry.minimumRadius,
                geometry.maximumGrade, templateId,
                displayName + (bridge ? "（桥梁）" : "") + (profile.median ? "（带中间隔离带）" : ""),
                bridge ? AutogenTemplateKind.BRIDGE : AutogenTemplateKind.GROUND);
    }

    private static void addMedianStructure(List<AutogenPlan.Operation> operations,
                                            RouteGeometry geometry, SurfaceLayout layout,
                                            MedianMaterials materials, AutogenBuildOrder buildOrder) {
        buildOrder.materials(AutogenBuildOrder.Stage.CONCRETE,
                        materials.concrete.getBlock(), materials.concreteSlab)
                .materials(AutogenBuildOrder.Stage.FIXTURES, materials.barrier,
                        materials.barrierCorner, materials.shield, materials.shieldCorner);
        for (int i = 0; i < geometry.route.size(); i++) {
            RoadSurface surface = layout.surfaceAt(key(geometry.route.get(i)));
            BlockPos support = surface.slab == null ? surface.base : surface.slab;
            operations.add(new AutogenPlan.Operation(support.up(),
                    medianState(geometry.route, i, materials.barrier,
                            materials.barrierCorner, surface.slab != null)));
            operations.add(new AutogenPlan.Operation(support.up(2),
                    medianState(geometry.route, i, materials.shield,
                            materials.shieldCorner, surface.slab != null)));
        }
    }

    private static IBlockState medianState(List<BlockPos> path, int index,
                                           Block straight, Block corner, boolean slab) {
        IBlockState state = lineState(path, index, straight, corner);
        if (!isCorner(path, index) && state.getValue(BlockHorizontal.FACING) == EnumFacing.WEST) {
            // The sample uses east-facing straight fixtures; both axis directions share a model.
            state = state.withProperty(BlockHorizontal.FACING, EnumFacing.EAST);
        }
        // Both fixture families encode the variant lowered by half a block in metadata bit 2.
        return slab ? state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state) + 4)
                : state;
    }

    private static void addBridgeStructure(List<AutogenPlan.Operation> operations,
                                            Footprint footprint, RouteGeometry geometry,
                                            SurfaceLayout layout, AutogenBuildOrder buildOrder)
            throws AutogenBuildException {
        // Resolve Railcraft only when a bridge was requested, preserving standalone ground roads.
        IBlockState concrete = Materials.resolve("railcraft:reinforced_concrete").getStateFromMeta(8);
        IBlockState fence = Materials.resolve("road_fence").getDefaultState();
        buildOrder.materials(AutogenBuildOrder.Stage.CONCRETE, concrete.getBlock())
                .materials(AutogenBuildOrder.Stage.FIXTURES, fence.getBlock());
        for (long cell : footprint.cells) {
            operations.add(new AutogenPlan.Operation(layout.surfaceAt(cell).base.down(), concrete));
        }

        Set<Long> edges = expand(footprint.cells, geometry);
        // The hand-built sample includes the exterior elbow at every diagonal corner.
        connectCorners(edges, footprint.cells, geometry);
        for (long cell : edges) {
            BlockPos base = layout.surfaceAt(cell).base;
            operations.add(new AutogenPlan.Operation(base, concrete));
            operations.add(new AutogenPlan.Operation(base.up(), concrete));
            operations.add(new AutogenPlan.Operation(base.up(2), fence));
        }
    }

    private static void validateSelection(WorldServer world,
                                          AutogenSelection.Selection selection)
            throws AutogenBuildException {
        if (selection == null || !selection.isComplete()) {
            throw new AutogenBuildException("请先用自动生成魔杖选择两个标记方块");
        }
        if (selection.start.dimension != selection.end.dimension
                || selection.start.dimension != world.provider.getDimension()) {
            throw new AutogenBuildException("两个标记必须与玩家位于同一维度");
        }
        for (AutogenSelection.Anchor anchor
                : new AutogenSelection.Anchor[]{selection.start, selection.end}) {
            IBlockState state = world.getBlockState(anchor.pos);
            if (!new ResourceLocation("nebulaecraft:autogen_marker")
                    .equals(state.getBlock().getRegistryName())) {
                throw new AutogenBuildException("已选择的标记方块不存在或已被移动");
            }
            if (state.getValue(BlockAutogenMarker.FACING) != anchor.facing) {
                throw new AutogenBuildException("标记在选择后被旋转，请用魔杖重新选择两个标记");
            }
        }
    }

    private static Footprint createFootprint(RouteGeometry geometry, GroundRoadProfile profile)
            throws AutogenBuildException {
        Set<Long> center = new HashSet<>();
        for (BlockPos pos : geometry.route) {
            center.add(key(pos));
        }
        Set<Long> interior = new HashSet<>(center);
        Map<Integer, List<BlockPos>> lines = new LinkedHashMap<>();
        lines.put(0, geometry.route);
        int last = geometry.route.size() - 1;
        for (int distance = 1; distance <= profile.halfWidth; distance++) {
            Set<Long> band = expand(interior, geometry);
            connectCorners(band, interior, geometry);
            // Discard corner spurs at every distance, including unmarked asphalt bands.
            // Expanding those spurs repeatedly can consume a wider band's endpoint anchors.
            for (int side : new int[]{1, -1}) {
                List<BlockPos> path = edgePath(band,
                        geometry.route.get(0).offset(
                                geometry.sectionFacings.get(0).rotateYCCW(), side * distance),
                        geometry.route.get(last).offset(
                                geometry.sectionFacings.get(last).rotateYCCW(), side * distance));
                path = alignBandTransitions(path, geometry, side > 0, distance,
                        // Keep the first yellow bands outside the occupied concrete centre.
                        // Odd-width rounding can otherwise move an inner corner onto the median.
                        profile.halfWidth % 2 != 0 && !profile.median);
                if (distance < profile.halfWidth) {
                    for (BlockPos pos : path) {
                        interior.add(key(pos));
                    }
                }
                if (profile.hasLineAtDistance(distance)) {
                    lines.put(side * distance, path);
                }
            }
        }
        Set<Long> cells = new HashSet<>(interior);
        Set<Long> lineCells = new HashSet<>();
        if (profile.hasLineAtDistance(0) || profile.median) {
            lineCells.addAll(center);
        }
        for (Map.Entry<Integer, List<BlockPos>> line : lines.entrySet()) {
            if (line.getKey() == 0) {
                continue;
            }
            List<BlockPos> path = line.getValue();
            for (int i = 0; i < path.size(); i++) {
                BlockPos pos = path.get(i);
                if (!lineCells.add(key(pos))) {
                    throw new AutogenBuildException("道路标线相交，请增大曲线半径");
                }
                if (i > 0 && horizontalStep(path.get(i - 1), pos) != 1) {
                    throw new AutogenBuildException("道路标线无法连续连接，请增大曲线半径");
                }
                cells.add(key(pos));
            }
        }
        return new Footprint(cells, lines);
    }

    private static int horizontalStep(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
    }

    /**
     * A diagonal pair spans two cells, so each two lateral cells need one row of stagger.
     * Even half-widths correct every second band. Odd half-widths need half-cell rounding:
     * floor the signed stagger on the world axis, correcting positive offsets on odd bands
     * and negative offsets on even bands. Correcting both sides on odd bands would align
     * adjacent marking pairs instead of keeping their one-row separation. World-axis rounding
     * also keeps the footprint unchanged when the selection direction is reversed.
     * This avoids accumulating long raw offsets that would run past a wide road's endpoints.
     */
    private static List<BlockPos> alignBandTransitions(
            List<BlockPos> path, RouteGeometry geometry, boolean left, int distance,
            boolean oddHalfWidth) {
        Map<Long, Integer> indices = new HashMap<>();
        for (int i = 0; i < path.size(); i++) {
            indices.put(key(path.get(i)), i);
        }
        List<BlockPos> result = new ArrayList<>(path);
        for (int i = 1; i + 2 < geometry.route.size(); i++) {
            BlockPos oldCorner = geometry.route.get(i);
            BlockPos newCorner = geometry.route.get(i + 1);
            EnumFacing incoming = direction(geometry.route.get(i - 1), oldCorner);
            EnumFacing shift = direction(oldCorner, newCorner);
            EnumFacing outgoing = direction(newCorner, geometry.route.get(i + 2));
            if (incoming != outgoing || incoming.getAxis() == shift.getAxis()) {
                continue;
            }
            EnumFacing side = left ? incoming.rotateYCCW() : incoming.rotateY();
            boolean shiftTowardEdge = shift == side;
            int axisDirection = incoming.getFrontOffsetX() + incoming.getFrontOffsetZ();
            boolean positiveOffset = (shiftTowardEdge ? -axisDirection : axisDirection) > 0;
            int correctionParity = oddHalfWidth && positiveOffset ? 1 : 0;
            if (distance % 2 != correctionParity) {
                continue;
            }
            int stagger = distance / 2 + 1;
            BlockPos first = new BlockPos(oldCorner.getX(), 0, oldCorner.getZ()).offset(side, distance)
                    .offset(incoming, shiftTowardEdge ? -stagger : stagger);
            BlockPos second = first.offset(shift);
            Integer firstIndex = indices.get(key(first));
            Integer secondIndex = indices.get(key(second));
            if (firstIndex == null || secondIndex == null
                    || secondIndex != firstIndex + 1
                    || !isCorner(path, firstIndex) || !isCorner(path, secondIndex)) {
                continue;
            }
            if (shiftTowardEdge) {
                result.set(secondIndex, first.offset(incoming));
            } else {
                result.set(firstIndex, second.offset(incoming.getOpposite()));
            }
        }
        return result;
    }

    private static Set<Long> expand(Set<Long> inner, RouteGeometry geometry) {
        Set<Long> band = new HashSet<>();
        for (long cell : inner) {
            BlockPos pos = BlockPos.fromLong(cell);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos next = pos.offset(direction);
                long nextKey = key(next);
                if (!inner.contains(nextKey) && !beyondEnd(next, geometry)) {
                    band.add(nextKey);
                }
            }
        }
        return band;
    }

    /** Fill only the exterior elbow of diagonal band fragments, preserving the inner material. */
    private static void connectCorners(Set<Long> band, Set<Long> inner,
                                       RouteGeometry geometry) {
        boolean changed;
        do {
            changed = false;
            List<Long> ordered = new ArrayList<>(band);
            Collections.sort(ordered);
            for (long cell : ordered) {
                BlockPos pos = BlockPos.fromLong(cell);
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dz = -1; dz <= 1; dz += 2) {
                        if (!band.contains(key(pos.add(dx, 0, dz)))) {
                            continue;
                        }
                        BlockPos first = pos.add(dx, 0, 0);
                        BlockPos second = pos.add(0, 0, dz);
                        boolean firstInner = inner.contains(key(first));
                        boolean secondInner = inner.contains(key(second));
                        if (firstInner == secondInner) {
                            continue;
                        }
                        BlockPos outer = firstInner ? second : first;
                        if (!beyondEnd(outer, geometry) && band.add(key(outer))) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
    }

    private static List<BlockPos> edgePath(Set<Long> edges, BlockPos start, BlockPos end)
            throws AutogenBuildException {
        long startKey = key(start);
        long endKey = key(end);
        Map<Long, Long> previous = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        if (edges.contains(startKey) && edges.contains(endKey)) {
            queue.add(startKey);
            previous.put(startKey, startKey);
        }
        while (!queue.isEmpty() && !previous.containsKey(endKey)) {
            long current = queue.removeFirst();
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                long next = key(BlockPos.fromLong(current).offset(direction));
                if (edges.contains(next) && !previous.containsKey(next)) {
                    previous.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!previous.containsKey(endKey)) {
            throw new AutogenBuildException("道路边线无法连续连接，请调整标记位置或方向");
        }
        List<BlockPos> path = new ArrayList<>();
        for (long current = endKey; ; current = previous.get(current)) {
            path.add(BlockPos.fromLong(current));
            if (current == startKey) {
                break;
            }
        }
        Collections.reverse(path);
        return path;
    }

    private static boolean beyondEnd(BlockPos pos, RouteGeometry geometry) {
        BlockPos start = geometry.route.get(0);
        int last = geometry.route.size() - 1;
        BlockPos end = geometry.route.get(last);
        EnumFacing startFacing = geometry.sectionFacings.get(0);
        EnumFacing endFacing = geometry.sectionFacings.get(last);
        return (pos.getX() - start.getX()) * startFacing.getFrontOffsetX()
                + (pos.getZ() - start.getZ()) * startFacing.getFrontOffsetZ() < 0
                || (pos.getX() - end.getX()) * endFacing.getFrontOffsetX()
                + (pos.getZ() - end.getZ()) * endFacing.getFrontOffsetZ() > 0;
    }

    private static void placeLine(Map<Long, IBlockState> states, List<BlockPos> path,
                                  Block straight, Block corner, int[] positions, int dashLength) {
        for (int i = 0; i < path.size(); i++) {
            // Every marking uses the same centre-section phase, including after a curve.
            int phase = positions == null ? 0 : positions[i] % (2 * dashLength);
            if (positions == null || phase >= 1 && phase <= dashLength) {
                states.put(key(path.get(i)), lineState(path, i, straight, corner));
            }
        }
    }

    /**
     * Like paired tunnel_metro_2 supports, offset markings share centre-route sections rather
     * than counting their unequal inner/outer lengths. Keep each diagonal pair in one phase.
     */
    private static int[] alignedLogicalPositions(List<BlockPos> path, SurfaceLayout layout,
                                                  int[] centerPositions) {
        int[] positions = new int[path.size()];
        int index = 0;
        while (index < path.size()) {
            int section = layout.sectionAt(key(path.get(index)));
            boolean pair = isCorner(path, index) && isCorner(path, index + 1);
            if (pair) {
                int nextSection = layout.sectionAt(key(path.get(index + 1)));
                section = (section + nextSection) / 2;
            }
            positions[index] = centerPositions[section];
            if (pair) {
                positions[index + 1] = positions[index];
                index += 2;
            } else {
                index++;
            }
        }
        return positions;
    }

    /** A pair of consecutive diagonal marking blocks shares a single dash/gap position. */
    private static int[] logicalPositions(List<BlockPos> path) {
        int[] positions = new int[path.size()];
        int logical = 0;
        int index = 0;
        while (index < path.size()) {
            positions[index] = logical;
            if (isCorner(path, index) && isCorner(path, index + 1)) {
                positions[index + 1] = logical;
                index += 2;
            } else {
                index++;
            }
            logical++;
        }
        return positions;
    }

    private static boolean isCorner(List<BlockPos> path, int index) {
        return index > 0 && index + 1 < path.size()
                && direction(path.get(index - 1), path.get(index)).getAxis()
                != direction(path.get(index), path.get(index + 1)).getAxis();
    }

    private static IBlockState lineState(List<BlockPos> path, int index,
                                         Block straight, Block corner) {
        BlockPos current = path.get(index);
        EnumFacing before = index > 0 ? direction(current, path.get(index - 1)) : null;
        EnumFacing after = index + 1 < path.size() ? direction(current, path.get(index + 1)) : null;
        if (before == null) {
            before = after.getOpposite();
        }
        if (after == null) {
            after = before.getOpposite();
        }
        if (before.getAxis() == after.getAxis()) {
            // Road addon models use facing=west for the reference's east-west stripe.
            EnumFacing facing = before.getAxis() == EnumFacing.Axis.X
                    ? EnumFacing.WEST : EnumFacing.NORTH;
            return straight.getDefaultState().withProperty(BlockHorizontal.FACING, facing);
        }
        // The unrotated (facing=east) diagonal model connects its north and east faces.
        boolean north = before == EnumFacing.NORTH || after == EnumFacing.NORTH;
        boolean east = before == EnumFacing.EAST || after == EnumFacing.EAST;
        EnumFacing facing = north ? (east ? EnumFacing.EAST : EnumFacing.NORTH)
                : (east ? EnumFacing.SOUTH : EnumFacing.WEST);
        return corner.getDefaultState().withProperty(BlockHorizontal.FACING, facing);
    }

    private static EnumFacing direction(BlockPos from, BlockPos to) {
        return EnumFacing.getFacingFromVector(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
    }

    private static long key(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ()).toLong();
    }

    private static List<AutogenPlan.PreviewFrame> createPreviewFrames(
            RouteGeometry geometry, int[] logicalPositions, int repeatLength,
            int halfWidth, SurfaceLayout layout, boolean bridge, boolean median) {
        List<AutogenPlan.PreviewFrame> frames = new ArrayList<>();
        int outerWidth = halfWidth + (bridge ? 1 : 0);
        int last = geometry.route.size() - 1;
        for (int i = 0; i <= last; i++) {
            BlockPos center = geometry.route.get(i);
            EnumFacing forward = geometry.sectionFacings.get(i);
            EnumFacing left = forward.rotateYCCW();
            double endOffset = i == 0 ? -0.5 : i == last ? 0.5 : 0;
            double x = center.getX() + 0.5 + forward.getFrontOffsetX() * endOffset;
            double z = center.getZ() + 0.5 + forward.getFrontOffsetZ() * endOffset;
            double leftX = x + left.getFrontOffsetX() * (outerWidth + 0.5);
            double leftZ = z + left.getFrontOffsetZ() * (outerWidth + 0.5);
            double rightX = x - left.getFrontOffsetX() * (outerWidth + 0.5);
            double rightZ = z - left.getFrontOffsetZ() * (outerWidth + 0.5);
            RoadSurface leftSurface = layout.surfaceAt(key(center.offset(left, outerWidth)));
            RoadSurface rightSurface = layout.surfaceAt(key(center.offset(left, -outerWidth)));
            double leftTop = bridge ? leftSurface.base.getY() + 3 : leftSurface.top();
            double rightTop = bridge ? rightSurface.base.getY() + 3 : rightSurface.top();
            if (median) {
                double medianTop = layout.surfaceAt(key(center)).top() + 1.625;
                leftTop = Math.max(leftTop, medianTop);
                rightTop = Math.max(rightTop, medianTop);
            }
            frames.add(new AutogenPlan.PreviewFrame(
                    new Vec3d(leftX, leftSurface.base.getY() - (bridge ? 1 : 0), leftZ),
                    new Vec3d(leftX, leftTop, leftZ),
                    new Vec3d(rightX, rightTop, rightZ),
                    new Vec3d(rightX, rightSurface.base.getY() - (bridge ? 1 : 0), rightZ),
                    i == 0 || i == last || logicalPositions[i] % repeatLength == 0
                    && logicalPositions[i] != logicalPositions[i - 1]));
        }
        return frames;
    }

    /** Quantize the original continuous road elevation into half-block surface levels. */
    private static final class SurfaceLayout {
        final RouteGeometry geometry;
        final Map<Long, Integer> sections = new HashMap<>();
        final Map<Long, RoadSurface> surfaces = new HashMap<>();

        SurfaceLayout(RouteGeometry geometry) {
            this.geometry = geometry;
            for (int i = 0; i < geometry.route.size(); i++) {
                sections.put(key(geometry.route.get(i)), i);
            }
        }

        RoadSurface surfaceAt(long cell) {
            RoadSurface cached = surfaces.get(cell);
            if (cached != null) {
                return cached;
            }
            BlockPos pos = BlockPos.fromLong(cell);
            double elevation = curveElevationAt(pos, sectionAt(cell));
            int halfLevel = (int) Math.round(elevation * 2);
            BlockPos base = new BlockPos(pos.getX(), (int) Math.round(elevation), pos.getZ());
            BlockPos slab = (halfLevel & 1) == 0 ? null
                    : new BlockPos(pos.getX(), Math.floorDiv(halfLevel, 2) + 1, pos.getZ());
            RoadSurface result = new RoadSurface(base, slab);
            surfaces.put(cell, result);
            return result;
        }

        private double curveElevationAt(BlockPos pos, int section) {
            int lastSample = geometry.curvePoints.size() - 1;
            int firstSection = Math.max(0, section - SECTION_SEARCH_RADIUS);
            int lastSection = Math.min(geometry.route.size() - 1, section + SECTION_SEARCH_RADIUS);
            int first = Math.max(0, (int) Math.floor(
                    geometry.sectionParameters.get(firstSection) * lastSample) - 1);
            int last = Math.min(lastSample, (int) Math.ceil(
                    geometry.sectionParameters.get(lastSection) * lastSample) + 1);
            double closest = Double.POSITIVE_INFINITY;
            double elevation = geometry.curvePoints.get(first).y;
            for (int i = first; i < last; i++) {
                RouteVector start = geometry.curvePoints.get(i);
                RouteVector end = geometry.curvePoints.get(i + 1);
                double dx = end.x - start.x;
                double dz = end.z - start.z;
                double lengthSq = dx * dx + dz * dz;
                double along = lengthSq > 1.0E-12
                        ? ((pos.getX() - start.x) * dx + (pos.getZ() - start.z) * dz) / lengthSq : 0;
                along = Math.max(0, Math.min(1, along));
                double offsetX = pos.getX() - (start.x + dx * along);
                double offsetZ = pos.getZ() - (start.z + dz * along);
                double distance = offsetX * offsetX + offsetZ * offsetZ;
                if (distance < closest) {
                    closest = distance;
                    elevation = start.y + (end.y - start.y) * along;
                }
            }
            return elevation;
        }

        private int sectionAt(long cell) {
            Integer cached = sections.get(cell);
            if (cached != null) {
                return cached;
            }
            BlockPos pos = BlockPos.fromLong(cell);
            int nearest = 0;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < geometry.route.size(); i++) {
                double distance = horizontalDistanceSq(pos, geometry.route.get(i));
                if (distance < bestDistance) {
                    nearest = i;
                    bestDistance = distance;
                }
            }
            int selected = nearest;
            double bestLongitudinal = Double.POSITIVE_INFINITY;
            bestDistance = Double.POSITIVE_INFINITY;
            for (int i = Math.max(0, nearest - SECTION_SEARCH_RADIUS);
                    i <= Math.min(geometry.route.size() - 1, nearest + SECTION_SEARCH_RADIUS); i++) {
                BlockPos center = geometry.route.get(i);
                RouteVector tangent = geometry.sectionTangents.get(i);
                double length = Math.hypot(tangent.x, tangent.z);
                double longitudinal = Math.abs(((pos.getX() - center.getX()) * tangent.x
                        + (pos.getZ() - center.getZ()) * tangent.z) / length);
                double distance = horizontalDistanceSq(pos, center);
                if (longitudinal < bestLongitudinal - 1.0E-8
                        || Math.abs(longitudinal - bestLongitudinal) <= 1.0E-8
                        && distance < bestDistance) {
                    selected = i;
                    bestLongitudinal = longitudinal;
                    bestDistance = distance;
                }
            }
            sections.put(cell, selected);
            return selected;
        }

        private double horizontalDistanceSq(BlockPos first, BlockPos second) {
            double dx = first.getX() - second.getX();
            double dz = first.getZ() - second.getZ();
            return dx * dx + dz * dz;
        }
    }

    private static final class RoadSurface {
        final BlockPos base;
        final BlockPos slab;

        RoadSurface(BlockPos base, BlockPos slab) {
            this.base = base;
            this.slab = slab;
        }

        double top() {
            return slab == null ? base.getY() + 1.0 : slab.getY() + 0.5;
        }
    }

    private static final class Footprint {
        final Set<Long> cells;
        final Map<Integer, List<BlockPos>> lines;

        Footprint(Set<Long> cells, Map<Integer, List<BlockPos>> lines) {
            this.cells = cells;
            this.lines = lines;
        }
    }

    private static final class Materials {
        final IBlockState asphalt = resolve("asphalt").getDefaultState();
        final Map<GroundRoadProfile.Marking, Block> straight =
                new EnumMap<>(GroundRoadProfile.Marking.class);
        final Map<GroundRoadProfile.Marking, Block> corners =
                new EnumMap<>(GroundRoadProfile.Marking.class);
        final Map<Block, Block> slabs = new HashMap<>();
        final MedianMaterials median;

        Materials(GroundRoadProfile profile) throws AutogenBuildException {
            slabs.put(asphalt.getBlock(), resolve("asphalt_slab"));
            for (GroundRoadProfile.Line line : profile.lines) {
                if (!straight.containsKey(line.marking)) {
                    String name = line.marking.blockName;
                    Block full = resolve(name);
                    Block corner = resolve(name + "_diagonal");
                    straight.put(line.marking, full);
                    corners.put(line.marking, corner);
                    slabs.put(full, resolve(name + "_slab"));
                    slabs.put(corner, resolve(name + "_diagonal_slab"));
                }
            }
            median = profile.median ? new MedianMaterials() : null;
            if (median != null) {
                slabs.put(median.concrete.getBlock(), median.concreteSlab);
            }
        }

        IBlockState slabState(IBlockState full) {
            if (median != null && full.getBlock() == median.concrete.getBlock()) {
                // Railcraft concrete uses metadata 8; our lower concrete slab uses metadata 0.
                return median.concreteSlab.getStateFromMeta(0);
            }
            // Each existing road/slab pair uses the same facing/subtype metadata encoding.
            return slabs.get(full.getBlock()).getStateFromMeta(full.getBlock().getMetaFromState(full));
        }

        private static Block resolve(String name) throws AutogenBuildException {
            ResourceLocation id = name.indexOf(':') >= 0
                    ? new ResourceLocation(name) : new ResourceLocation("nebulaecraft", name);
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null || block == Blocks.AIR) {
                throw new AutogenBuildException("找不到道路模板所需方块: " + id);
            }
            return block;
        }
    }

    private static final class MedianMaterials {
        final IBlockState concrete = Materials.resolve("railcraft:reinforced_concrete").getStateFromMeta(8);
        final Block concreteSlab = Materials.resolve("reinforced_concrete_slab");
        final Block barrier = Materials.resolve("median_strips");
        final Block barrierCorner = Materials.resolve("median_strips_diagonal");
        final Block shield = Materials.resolve("antiglare_shield");
        final Block shieldCorner = Materials.resolve("antiglare_shield_diagonal");

        MedianMaterials() throws AutogenBuildException {
        }
    }
}
