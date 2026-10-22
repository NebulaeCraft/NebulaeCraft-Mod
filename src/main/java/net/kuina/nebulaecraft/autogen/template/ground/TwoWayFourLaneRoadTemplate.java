package net.kuina.nebulaecraft.autogen.template.ground;

/** Nine-wide road with a solid double yellow center and two dashed white lane dividers. */
public final class TwoWayFourLaneRoadTemplate extends GroundRoadTemplate {
    public static final String ID = "ground_road_two_way_4";

    public TwoWayFourLaneRoadTemplate() {
        super(ID, "双向四车道道路", GroundRoadProfile.TWO_WAY_FOUR_LANE);
    }
}
