package net.kuina.nebulaecraft.autogen.route;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared route result that tunnel and bridge templates can sweep with their own cross-sections. */
public final class RouteGeometry {
    public final List<BlockPos> route;
    public final List<EnumFacing> sectionFacings;
    public final List<RouteVector> sectionTangents;
    public final double length;
    public final double minimumRadius;
    public final double maximumGrade;

    RouteGeometry(List<BlockPos> route, List<EnumFacing> sectionFacings,
                  List<RouteVector> sectionTangents, double length,
                  double minimumRadius, double maximumGrade) {
        this.route = immutableCopy(route);
        this.sectionFacings = immutableCopy(sectionFacings);
        this.sectionTangents = immutableCopy(sectionTangents);
        this.length = length;
        this.minimumRadius = minimumRadius;
        this.maximumGrade = maximumGrade;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
