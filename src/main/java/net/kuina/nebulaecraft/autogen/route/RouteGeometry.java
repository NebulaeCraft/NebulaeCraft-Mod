package net.kuina.nebulaecraft.autogen.route;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared raster route and continuous curve data for automatic structure templates. */
public final class RouteGeometry {
    public final List<BlockPos> route;
    public final List<EnumFacing> sectionFacings;
    public final List<RouteVector> sectionTangents;
    /** Original curve parameter of each raster section, before railway grade alignment. */
    public final List<Double> sectionParameters;
    /** Dense continuous curve samples, including their unrounded elevations. */
    public final List<RouteVector> curvePoints;
    public final double length;
    public final double minimumRadius;
    public final double maximumGrade;

    RouteGeometry(List<BlockPos> route, List<EnumFacing> sectionFacings,
                  List<RouteVector> sectionTangents, List<Double> sectionParameters,
                  List<RouteVector> curvePoints, double length,
                  double minimumRadius, double maximumGrade) {
        this.route = immutableCopy(route);
        this.sectionFacings = immutableCopy(sectionFacings);
        this.sectionTangents = immutableCopy(sectionTangents);
        this.sectionParameters = immutableCopy(sectionParameters);
        this.curvePoints = immutableCopy(curvePoints);
        this.length = length;
        this.minimumRadius = minimumRadius;
        this.maximumGrade = maximumGrade;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
