package net.kuina.nebulaecraft.autogen.template.ground;

/** Thirteen-wide road with a double yellow center and four fixed 3+3 white lane dividers. */
public final class TwoWaySixLaneRoadTemplate extends GroundRoadTemplate {
    public static final String ID = "ground_road_two_way_6";

    public TwoWaySixLaneRoadTemplate() {
        super(ID, "双向六车道道路", GroundRoadProfile.TWO_WAY_SIX_LANE, 3);
    }
}
