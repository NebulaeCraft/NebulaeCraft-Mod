package net.kuina.nebulaecraft.autogen.route;

import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared quintic route planner for rail tunnels and railway bridges.
 *
 * <p>The returned route contains cardinal one-block steps. Height changes are moved onto straight,
 * tangent-aligned rail edges because Minecraft 1.12 rail metadata cannot represent a slope and a
 * horizontal curve in the same block.</p>
 */
public final class RoutePlanner {
    private RoutePlanner() {
    }

    public static RouteGeometry plan(AutogenSelection.Selection selection,
                                     double minimumRadius, double maximumGrade)
            throws RouteBuildException {
        if (minimumRadius <= 0.0 || maximumGrade <= 0.0
                || Double.isNaN(minimumRadius) || Double.isInfinite(minimumRadius)
                || Double.isNaN(maximumGrade) || Double.isInfinite(maximumGrade)) {
            throw new RouteBuildException("路线曲率和坡度限制必须为有限正数");
        }
        if (selection == null || !selection.isComplete()) {
            throw new RouteBuildException("请先用自动生成魔杖选择两个标记方块");
        }
        AutogenSelection.Anchor start = selection.start;
        AutogenSelection.Anchor end = selection.end;
        double dx = end.pos.getX() - start.pos.getX();
        double dz = end.pos.getZ() - start.pos.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 3.0) {
            throw new RouteBuildException("两个标记的水平距离至少需要 3 格");
        }
        double startDot = start.facing.getFrontOffsetX() * dx
                + start.facing.getFrontOffsetZ() * dz;
        double endDot = end.facing.getFrontOffsetX() * -dx
                + end.facing.getFrontOffsetZ() * -dz;
        if (startDot <= 0 || endDot <= 0) {
            throw new RouteBuildException("两个标记的箭头必须朝向彼此所在的一侧");
        }

        RouteVector p0 = new RouteVector(
                start.pos.getX(), start.pos.getY(), start.pos.getZ());
        RouteVector p1 = new RouteVector(
                end.pos.getX(), end.pos.getY(), end.pos.getZ());
        RouteVector v0 = new RouteVector(
                start.facing.getFrontOffsetX() * horizontalDistance, 0,
                start.facing.getFrontOffsetZ() * horizontalDistance);
        RouteVector v1 = new RouteVector(
                -end.facing.getFrontOffsetX() * horizontalDistance, 0,
                -end.facing.getFrontOffsetZ() * horizontalDistance);
        int samples = Math.max(64, (int) Math.ceil(horizontalDistance * 8.0));
        List<RouteVector> points = new ArrayList<>(samples + 1);
        double length = 0;
        double minRadius = Double.POSITIVE_INFINITY;
        double maxGrade = 0;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            RouteVector point = quintic(p0, p1, v0, v1, t);
            points.add(point);
            if (i > 0) {
                RouteVector delta = point.subtract(points.get(i - 1));
                length += delta.length();
                double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                if (horizontal > 1.0E-6) {
                    maxGrade = Math.max(maxGrade, Math.abs(delta.y) / horizontal);
                }
            }
            if (i > 1) {
                RouteVector a = points.get(i - 1).subtract(points.get(i - 2));
                RouteVector b = point.subtract(points.get(i - 1));
                double ah = Math.sqrt(a.x * a.x + a.z * a.z);
                double bh = Math.sqrt(b.x * b.x + b.z * b.z);
                if (ah > 1.0E-6 && bh > 1.0E-6) {
                    double cosine = clamp(
                            (a.x * b.x + a.z * b.z) / (ah * bh), -1, 1);
                    double angle = Math.acos(cosine);
                    if (angle > 1.0E-5) {
                        minRadius = Math.min(minRadius, ((ah + bh) * 0.5) / angle);
                    }
                }
            }
        }
        if (minRadius < minimumRadius) {
            throw new RouteBuildException(String.format(Locale.ROOT,
                    "曲线最小半径 %.2f 小于模板限制 %.2f", minRadius, minimumRadius));
        }
        if (maxGrade > maximumGrade) {
            throw new RouteBuildException(String.format(Locale.ROOT,
                    "最大坡度 %.2f%% 超过模板限制 %.2f%%",
                    maxGrade * 100, maximumGrade * 100));
        }

        RasterizedRoute rasterized = rasterize(points, start.pos, end.pos);
        if (rasterized.positions.size() < 2) {
            throw new RouteBuildException("无法将曲线转换为连续轨道");
        }
        List<RouteVector> sectionTangents = curveTangents(
                rasterized.parameters, p0, p1, v0, v1);
        List<EnumFacing> sectionFacings = curveTangentFacings(sectionTangents, v0);
        List<BlockPos> gradedRoute = alignGradesToCurveSections(
                rasterized.positions, rasterized.parameters, sectionFacings,
                p0, p1, v0, v1, maximumGrade);
        return new RouteGeometry(gradedRoute, sectionFacings, sectionTangents,
                length, minRadius, maxGrade);
    }

    private static RouteVector quintic(RouteVector p0, RouteVector p1,
                                       RouteVector v0, RouteVector v1, double t) {
        RouteVector d = p1.subtract(p0);
        RouteVector c3 = d.scale(10).subtract(v0.scale(6)).subtract(v1.scale(4));
        RouteVector c4 = d.scale(-15).add(v0.scale(8)).add(v1.scale(7));
        RouteVector c5 = d.scale(6).subtract(v0.scale(3)).subtract(v1.scale(3));
        return p0.add(v0.scale(t)).add(c3.scale(t * t * t))
                .add(c4.scale(t * t * t * t)).add(c5.scale(t * t * t * t * t));
    }

    private static RouteVector quinticTangent(RouteVector p0, RouteVector p1,
                                              RouteVector v0, RouteVector v1, double t) {
        RouteVector d = p1.subtract(p0);
        RouteVector c3 = d.scale(10).subtract(v0.scale(6)).subtract(v1.scale(4));
        RouteVector c4 = d.scale(-15).add(v0.scale(8)).add(v1.scale(7));
        RouteVector c5 = d.scale(6).subtract(v0.scale(3)).subtract(v1.scale(3));
        return v0.add(c3.scale(3 * t * t))
                .add(c4.scale(4 * t * t * t))
                .add(c5.scale(5 * t * t * t * t));
    }

    private static List<RouteVector> curveTangents(
            List<Double> parameters, RouteVector p0, RouteVector p1,
            RouteVector v0, RouteVector v1) {
        List<RouteVector> tangents = new ArrayList<>(parameters.size());
        for (double parameter : parameters) {
            tangents.add(quinticTangent(p0, p1, v0, v1, parameter));
        }
        return tangents;
    }

    private static List<EnumFacing> curveTangentFacings(
            List<RouteVector> tangents, RouteVector initialTangent) {
        List<EnumFacing> facings = new ArrayList<>(tangents.size());
        EnumFacing previous = dominantFacing(initialTangent.x, initialTangent.z);
        for (RouteVector tangent : tangents) {
            if (Math.abs(tangent.x) > 1.0E-8 || Math.abs(tangent.z) > 1.0E-8) {
                previous = dominantFacing(tangent.x, tangent.z);
            }
            facings.add(previous);
        }
        return facings;
    }

    private static RasterizedRoute rasterize(List<RouteVector> points,
                                             BlockPos start, BlockPos end)
            throws RouteBuildException {
        List<BlockPos> result = new ArrayList<>();
        List<Double> parameters = new ArrayList<>();
        BlockPos current = start;
        result.add(current);
        parameters.add(0.0);
        Set<Long> visitedXZ = new HashSet<>();
        visitedXZ.add(xzKey(current.getX(), current.getZ()));
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            RouteVector point = points.get(pointIndex);
            double parameter = pointIndex / (double) (points.size() - 1);
            int targetX = (int) Math.round(point.x);
            int targetZ = (int) Math.round(point.z);
            while (current.getX() != targetX || current.getZ() != targetZ) {
                int sx = Integer.signum(targetX - current.getX());
                int sz = Integer.signum(targetZ - current.getZ());
                boolean moveX = sx != 0 && (sz == 0
                        || Math.abs(point.x - current.getX())
                        >= Math.abs(point.z - current.getZ()));
                int nextX = current.getX() + (moveX ? sx : 0);
                int nextZ = current.getZ() + (moveX ? 0 : sz);
                BlockPos next = new BlockPos(nextX, start.getY(), nextZ);
                long key = xzKey(nextX, nextZ);
                if (visitedXZ.contains(key)) {
                    throw new RouteBuildException(
                            "曲线在方格化后发生自交，请增大标记间距或调整方向");
                }
                visitedXZ.add(key);
                result.add(next);
                parameters.add(parameter);
                current = next;
            }
        }
        if (current.getX() != end.getX() || current.getZ() != end.getZ()) {
            throw new RouteBuildException("轨道路径没有到达终点");
        }
        return new RasterizedRoute(result, parameters);
    }

    private static List<BlockPos> alignGradesToCurveSections(
            List<BlockPos> horizontalRoute, List<Double> parameters,
            List<EnumFacing> sectionFacings, RouteVector p0, RouteVector p1,
            RouteVector v0, RouteVector v1, double maximumGrade)
            throws RouteBuildException {
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
            throw new RouteBuildException("坡度无法转换为连续的轨道台阶");
        }
        return result;
    }

    private static List<Integer> selectGradeEdges(
            boolean[] eligible, List<Integer> desiredEdges,
            int minimumSpacing, int direction) throws RouteBuildException {
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
            throw new RouteBuildException(String.format(Locale.ROOT,
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

    private static int turn(List<BlockPos> route, int index) {
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

    private static EnumFacing horizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.signum(to.getX() - from.getX());
        int dz = Integer.signum(to.getZ() - from.getZ());
        if (dx > 0) return EnumFacing.EAST;
        if (dx < 0) return EnumFacing.WEST;
        if (dz > 0) return EnumFacing.SOUTH;
        if (dz < 0) return EnumFacing.NORTH;
        return null;
    }

    private static EnumFacing dominantFacing(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? EnumFacing.WEST : EnumFacing.EAST;
        }
        return dz < 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
    }

    private static long xzKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class RasterizedRoute {
        final List<BlockPos> positions;
        final List<Double> parameters;

        RasterizedRoute(List<BlockPos> positions, List<Double> parameters) {
            this.positions = positions;
            this.parameters = parameters;
        }
    }
}
