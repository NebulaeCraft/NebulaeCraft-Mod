package net.kuina.nebulaecraft.autogen.route;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;

/** Route-planning failure shared by tunnel and bridge templates. */
public final class RouteBuildException extends AutogenBuildException {
    public RouteBuildException(String message) {
        super(message);
    }
}
