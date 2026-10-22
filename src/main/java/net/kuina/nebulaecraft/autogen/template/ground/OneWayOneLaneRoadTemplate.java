package net.kuina.nebulaecraft.autogen.template.ground;

/** Three-wide road with a solid yellow left edge and a solid white right edge. */
public final class OneWayOneLaneRoadTemplate extends GroundRoadTemplate {
    public static final String ID = "ground_road_one_way_1";

    public OneWayOneLaneRoadTemplate() {
        super(ID, "单向单车道道路", GroundRoadProfile.ONE_WAY_ONE_LANE);
    }
}
