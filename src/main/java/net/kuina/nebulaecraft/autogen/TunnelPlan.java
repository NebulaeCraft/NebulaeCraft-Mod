package net.kuina.nebulaecraft.autogen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

public final class TunnelPlan {
    public final int dimension;
    public final List<Operation> operations;
    public final List<BlockPos> route;
    public final double length;
    public final double minimumRadius;
    public final double maximumGrade;
    public final String preset;
    public final String platformColor;
    public final String power;
    public final boolean mirrored;

    public TunnelPlan(int dimension, List<Operation> operations, List<BlockPos> route, double length,
                      double minimumRadius, double maximumGrade, String preset, String platformColor,
                      String power, boolean mirrored) {
        this.dimension = dimension;
        this.operations = Collections.unmodifiableList(operations);
        this.route = Collections.unmodifiableList(route);
        this.length = length;
        this.minimumRadius = minimumRadius;
        this.maximumGrade = maximumGrade;
        this.preset = preset;
        this.platformColor = platformColor;
        this.power = power;
        this.mirrored = mirrored;
    }

    public static final class Operation {
        public final BlockPos pos;
        public final IBlockState state;

        public Operation(BlockPos pos, IBlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }
}
