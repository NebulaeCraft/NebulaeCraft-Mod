package net.kuina.nebulaecraft.autogen.template.bridge;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenConfig;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.route.RouteGeometry;
import net.kuina.nebulaecraft.autogen.route.RoutePlanner;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.kuina.nebulaecraft.autogen.template.tunnel.MetroTunnel2Builder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builder owned exclusively by the double-track metro bridge template. */
public final class MetroBridge2Builder {
    private static final double MINIMUM_RADIUS = 12.0;
    private static final double MAXIMUM_GRADE = 0.1;
    private static final int TRACK_Y = 1;
    private static final int CATENARY_Y = 5;
    private static final int HALF_WIDTH = 4;

    private MetroBridge2Builder() {
    }

    public static AutogenPlan build(
            WorldServer world, AutogenSelection.Selection selection,
            String templateId, String displayName) throws AutogenBuildException {
        MetroBridge1Builder.validateSelection(world, selection);
        RouteGeometry geometry = RoutePlanner.plan(
                selection, MINIMUM_RADIUS, MAXIMUM_GRADE);
        AutogenConfig.Settings settings = AutogenConfig.get();
        if (geometry.length > settings.maxPathLength) {
            throw new AutogenBuildException(
                    "路线长度超过配置上限 " + settings.maxPathLength);
        }
        MetroBridge1Builder.validateBuildHeight(world, geometry.route);

        MetroBridge1Builder.Materials materials =
                new MetroBridge1Builder.Materials();
        MetroTunnel2Builder.DoubleTrackPlan doubleTrack =
                MetroTunnel2Builder.planDoubleTrack(geometry);
        Footprint footprint = createFootprint(doubleTrack, geometry.route);
        LinkedHashMap<Long, MetroBridge1Builder.PlannedState> states =
                new LinkedHashMap<>();
        planReferenceVolume(states, footprint, geometry.route, materials);

        List<AutogenPlan.Operation> railFinalizations = new ArrayList<>();
        MetroBridge1Builder.placeTracks(states, doubleTrack.getFirstTrack(),
                materials.reinforcedTrack, railFinalizations);
        MetroBridge1Builder.placeTracks(states, doubleTrack.getSecondTrack(),
                materials.reinforcedTrack, railFinalizations);
        placeCatenary(states, geometry, doubleTrack,
                footprint, true, materials);
        placeCatenary(states, geometry, doubleTrack,
                footprint, false, materials);

        int operationCount = states.size() + railFinalizations.size();
        if (operationCount > settings.maxChangedBlocks) {
            throw new AutogenBuildException(
                    "预计修改方块数超过配置上限 " + settings.maxChangedBlocks);
        }
        List<MetroBridge1Builder.PlannedState> ordered =
                new ArrayList<>(states.values());
        Collections.sort(ordered,
                Comparator.comparingInt(value -> value.priority));
        List<AutogenPlan.Operation> operations = new ArrayList<>(operationCount);
        for (MetroBridge1Builder.PlannedState state : ordered) {
            operations.add(new AutogenPlan.Operation(state.pos, state.state));
        }
        operations.addAll(railFinalizations);

        return new AutogenPlan(world.provider.getDimension(), operations,
                new ArrayList<>(geometry.route),
                MetroBridge1Builder.createPreviewFrames(geometry, HALF_WIDTH),
                geometry.length, geometry.minimumRadius, geometry.maximumGrade,
                templateId, displayName, AutogenTemplateKind.BRIDGE);
    }

    private static void planReferenceVolume(
            Map<Long, MetroBridge1Builder.PlannedState> states,
            Footprint footprint, List<BlockPos> centerRoute,
            MetroBridge1Builder.Materials materials) {
        for (BridgeColumn column : footprint.columns.values()) {
            BlockPos deck = column.deck;
            MetroBridge1Builder.put(
                    states, deck.down(), Blocks.AIR.getDefaultState(), 10);
            for (int y = TRACK_Y; y <= CATENARY_Y; y++) {
                MetroBridge1Builder.put(
                        states, deck.up(y), Blocks.AIR.getDefaultState(), 10);
            }
            if (column.trackbed) {
                MetroBridge1Builder.put(
                        states, deck.down(), materials.concrete, 20);
                MetroBridge1Builder.put(states, deck, column.solidTrackbed
                        ? materials.centerTrackbed : materials.sideTrackbed, 30);
            } else {
                MetroBridge1Builder.put(states, deck, materials.concrete, 30);
                MetroBridge1Builder.put(
                        states, deck.up(TRACK_Y), materials.fence, 40);
            }
        }
        // The selected marker line is tunnel_metro_2's one-block air seam at trackbed level,
        // but bridge_metro_1's reinforced-concrete deck remains continuous below both tracks.
        for (BlockPos center : centerRoute) {
            MetroBridge1Builder.put(
                    states, center.down(), materials.concrete, 25);
            for (int y = 0; y <= CATENARY_Y; y++) {
                MetroBridge1Builder.put(
                        states, center.up(y), Blocks.AIR.getDefaultState(), 45);
            }
        }
    }

    private static Footprint createFootprint(
            MetroTunnel2Builder.DoubleTrackPlan doubleTrack,
            List<BlockPos> centerRoute) {
        LinkedHashMap<Long, BridgeColumn> columns = new LinkedHashMap<>();
        Set<Long> trackbedCells = new HashSet<>();
        List<BridgeColumn> trackbedColumns = new ArrayList<>();
        for (long packed : doubleTrack.getCenterTrackbedCells()) {
            BlockPos deck = BlockPos.fromLong(packed);
            trackbedCells.add(MetroBridge1Builder.xzKey(
                    deck.getX(), deck.getZ()));
            BridgeColumn column = new BridgeColumn(deck, true, true);
            columns.put(deck.toLong(), column);
            trackbedColumns.add(column);
        }
        for (Map.Entry<Long, Boolean> entry
                : doubleTrack.getSideTrackbedCells().entrySet()) {
            BlockPos deck = BlockPos.fromLong(entry.getKey());
            if (columns.containsKey(deck.toLong())) {
                continue;
            }
            trackbedCells.add(MetroBridge1Builder.xzKey(
                    deck.getX(), deck.getZ()));
            BridgeColumn column = new BridgeColumn(
                    deck, true, Boolean.TRUE.equals(entry.getValue()));
            columns.put(deck.toLong(), column);
            trackbedColumns.add(column);
        }

        Set<Long> edgeCells = new HashSet<>();
        Collections.sort(trackbedColumns,
                Comparator.comparingLong(value -> value.deck.toLong()));
        for (BridgeColumn source : trackbedColumns) {
            long key = MetroBridge1Builder.xzKey(
                    source.deck.getX(), source.deck.getZ());
            long sourceDistance = MetroBridge1Builder.horizontalDistanceToRoute(
                    source.deck, centerRoute);
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                int edgeX = MetroBridge1Builder.xFromKey(key)
                        + direction.getFrontOffsetX();
                int edgeZ = MetroBridge1Builder.zFromKey(key)
                        + direction.getFrontOffsetZ();
                long edge = MetroBridge1Builder.xzKey(edgeX, edgeZ);
                if (trackbedCells.contains(edge)
                        || MetroBridge1Builder.isBeyondOpenRouteEnd(
                        edgeX, edgeZ, centerRoute)) {
                    continue;
                }
                BlockPos candidate = doubleTrack.floorAt(
                        new BlockPos(edgeX, source.deck.getY(), edgeZ));
                if (MetroBridge1Builder.horizontalDistanceToRoute(
                        candidate, centerRoute) > sourceDistance) {
                    edgeCells.add(edge);
                }
            }
        }
        MetroBridge1Builder.connectBandCardinally(
                edgeCells, trackbedCells, centerRoute);

        List<Long> orderedEdges = new ArrayList<>(edgeCells);
        Collections.sort(orderedEdges);
        for (long key : orderedEdges) {
            if (trackbedCells.contains(key)) {
                continue;
            }
            BlockPos deck = doubleTrack.floorAt(new BlockPos(
                    MetroBridge1Builder.xFromKey(key),
                    centerRoute.get(0).getY(),
                    MetroBridge1Builder.zFromKey(key)));
            columns.put(deck.toLong(), new BridgeColumn(deck, false, false));
        }
        return new Footprint(columns);
    }

    private static void placeCatenary(
            Map<Long, MetroBridge1Builder.PlannedState> states,
            RouteGeometry geometry,
            MetroTunnel2Builder.DoubleTrackPlan doubleTrack,
            Footprint footprint, boolean firstTrack,
            MetroBridge1Builder.Materials materials)
            throws AutogenBuildException {
        List<BlockPos> route = firstTrack
                ? doubleTrack.getFirstTrack() : doubleTrack.getSecondTrack();
        int[] logicalPositions =
                MetroBridge1Builder.catenaryLogicalPositions(route);
        Set<Integer> supports = findSupportIndices(
                geometry, doubleTrack, footprint, firstTrack,
                route, logicalPositions);
        Set<Integer> droppers = MetroBridge1Builder.findDropperIndices(
                route, supports, logicalPositions);
        for (int i = 0; i < route.size(); i++) {
            int section = sourceSection(doubleTrack, firstTrack, i);
            EnumFacing curveFacing = geometry.sectionFacings.get(section);
            BlockPos trackbed = route.get(i);
            IBlockState wire = MetroBridge1Builder.catenaryState(
                    route, i, curveFacing, supports, droppers, materials);
            MetroBridge1Builder.put(
                    states, trackbed.up(CATENARY_Y), wire, 50);
            if (!supports.contains(i)) {
                continue;
            }
            EnumFacing poleSide = poleSide(
                    geometry, doubleTrack, firstTrack, i);
            BlockPos poleBase = trackbed.offset(poleSide, 2);
            for (int y = TRACK_Y; y <= CATENARY_Y; y++) {
                MetroBridge1Builder.put(states, poleBase.up(y),
                        materials.catenaryPole.getDefaultState(), 60);
            }
            MetroBridge1Builder.put(
                    states, trackbed.offset(poleSide).up(CATENARY_Y),
                    MetroBridge1Builder.nebulaFacingState(
                            materials.catenarySupport,
                            poleSide.getOpposite()), 61);
        }
    }

    private static Set<Integer> findSupportIndices(
            RouteGeometry geometry,
            MetroTunnel2Builder.DoubleTrackPlan doubleTrack,
            Footprint footprint, boolean firstTrack,
            List<BlockPos> route, int[] logicalPositions) {
        return MetroBridge1Builder.scheduleSupportIndices(
                route.size(), logicalPositions,
                index -> isSupportCompatible(
                        geometry, doubleTrack, footprint,
                        firstTrack, route, index));
    }

    private static boolean isSupportCompatible(
            RouteGeometry geometry,
            MetroTunnel2Builder.DoubleTrackPlan doubleTrack,
            Footprint footprint, boolean firstTrack,
            List<BlockPos> route, int index) {
        if (MetroBridge1Builder.turn(route, index) != 0
                || MetroBridge1Builder.isSlopeRailCell(route, index)) {
            return false;
        }
        BlockPos trackbed = route.get(index);
        EnumFacing poleSide = poleSide(
                geometry, doubleTrack, firstTrack, index);
        BridgeColumn supportColumn = footprint.columns.get(
                trackbed.offset(poleSide).toLong());
        BridgeColumn poleColumn = footprint.columns.get(
                trackbed.offset(poleSide, 2).toLong());
        boolean concretePoleBase = poleColumn != null
                && !poleColumn.trackbed;
        // Match bridge_metro_1: a curved, raster-widened bridge can move the concrete edge
        // outward and leave the nominal pole position on an ordinary 44:0 side slab. It is
        // still a valid pole base; only the solid 43:8 slope bed remains incompatible.
        boolean sideTrackbedPoleBase = poleColumn != null
                && poleColumn.trackbed && !poleColumn.solidTrackbed;
        return supportColumn != null && poleColumn != null
                && supportColumn.trackbed
                && (concretePoleBase || sideTrackbedPoleBase)
                && supportColumn.deck.getY() == trackbed.getY()
                && poleColumn.deck.getY() == trackbed.getY();
    }

    private static EnumFacing poleSide(
            RouteGeometry geometry,
            MetroTunnel2Builder.DoubleTrackPlan doubleTrack,
            boolean firstTrack, int trackIndex) {
        EnumFacing forward = geometry.sectionFacings.get(
                sourceSection(doubleTrack, firstTrack, trackIndex));
        return firstTrack ? forward.rotateYCCW() : forward.rotateY();
    }

    private static int sourceSection(
            MetroTunnel2Builder.DoubleTrackPlan doubleTrack,
            boolean firstTrack, int trackIndex) {
        return firstTrack ? doubleTrack.getFirstSourceSection(trackIndex)
                : doubleTrack.getSecondSourceSection(trackIndex);
    }

    private static final class Footprint {
        final Map<Long, BridgeColumn> columns;

        Footprint(Map<Long, BridgeColumn> columns) {
            this.columns = columns;
        }
    }

    private static final class BridgeColumn {
        final BlockPos deck;
        final boolean trackbed;
        final boolean solidTrackbed;

        BridgeColumn(BlockPos deck, boolean trackbed,
                     boolean solidTrackbed) {
            this.deck = deck;
            this.trackbed = trackbed;
            this.solidTrackbed = solidTrackbed;
        }
    }
}
