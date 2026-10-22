package net.kuina.nebulaecraft.autogen.template.ground;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lane-count-based cross-sections using the spacing of the reference ground roads. */
final class GroundRoadProfile {
    static final GroundRoadProfile ONE_WAY_ONE_LANE = oneWay(1);
    static final GroundRoadProfile ONE_WAY_TWO_LANE = oneWay(2);
    static final GroundRoadProfile TWO_WAY_TWO_LANE = twoWay(1);
    static final GroundRoadProfile TWO_WAY_FOUR_LANE = twoWay(2);

    final int halfWidth;
    final List<Line> lines;

    private GroundRoadProfile(int halfWidth, List<Line> lines) {
        this.halfWidth = halfWidth;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /** One asphalt cell per lane, with a marking cell between adjacent lanes. */
    static GroundRoadProfile oneWay(int lanes) {
        requireLanes(lanes);
        List<Line> lines = new ArrayList<>();
        lines.add(line(lanes, Marking.YELLOW, false));
        for (int lane = 1; lane < lanes; lane++) {
            lines.add(line(lanes - 2 * lane, Marking.WHITE, true));
        }
        lines.add(line(-lanes, Marking.WHITE, false));
        return new GroundRoadProfile(lanes, lines);
    }

    /** Symmetric two-way road; the argument counts lanes in each direction. */
    static GroundRoadProfile twoWay(int lanesPerDirection) {
        requireLanes(lanesPerDirection);
        int halfWidth = Math.multiplyExact(2, lanesPerDirection);
        List<Line> lines = new ArrayList<>();
        lines.add(line(halfWidth, Marking.WHITE, false));
        for (int lane = lanesPerDirection - 1; lane >= 1; lane--) {
            lines.add(line(2 * lane, Marking.WHITE, true));
        }
        lines.add(line(0, lanesPerDirection == 1 ? Marking.YELLOW : Marking.DOUBLE_YELLOW,
                lanesPerDirection == 1));
        for (int lane = 1; lane < lanesPerDirection; lane++) {
            lines.add(line(-2 * lane, Marking.WHITE, true));
        }
        lines.add(line(-halfWidth, Marking.WHITE, false));
        return new GroundRoadProfile(halfWidth, lines);
    }

    private static void requireLanes(int lanes) {
        if (lanes < 1) {
            throw new IllegalArgumentException("Roads need at least one lane per direction");
        }
    }

    boolean hasDashedLines() {
        for (Line line : lines) {
            if (line.dashed) {
                return true;
            }
        }
        return false;
    }

    boolean hasLineAtDistance(int distance) {
        for (Line line : lines) {
            if (Math.abs(line.leftOffset) == distance) {
                return true;
            }
        }
        return false;
    }

    private static Line line(int leftOffset, Marking marking, boolean dashed) {
        return new Line(leftOffset, marking, dashed);
    }

    enum Marking {
        WHITE("asphalt_white_line"),
        YELLOW("asphalt_yellow_line"),
        DOUBLE_YELLOW("asphalt_double_yellow_line");

        final String blockName;

        Marking(String blockName) {
            this.blockName = blockName;
        }
    }

    static final class Line {
        /** Positive offsets lie to the left when travelling from the first marker to the second. */
        final int leftOffset;
        final Marking marking;
        final boolean dashed;

        Line(int leftOffset, Marking marking, boolean dashed) {
            this.leftOffset = leftOffset;
            this.marking = marking;
            this.dashed = dashed;
        }
    }
}
