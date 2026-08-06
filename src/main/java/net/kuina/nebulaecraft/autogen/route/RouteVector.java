package net.kuina.nebulaecraft.autogen.route;

/** Immutable double-precision vector used by route planning and template cross-sections. */
public final class RouteVector {
    public final double x;
    public final double y;
    public final double z;

    public RouteVector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public RouteVector add(RouteVector other) {
        return new RouteVector(x + other.x, y + other.y, z + other.z);
    }

    public RouteVector subtract(RouteVector other) {
        return new RouteVector(x - other.x, y - other.y, z - other.z);
    }

    public RouteVector scale(double scale) {
        return new RouteVector(x * scale, y * scale, z * scale);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
