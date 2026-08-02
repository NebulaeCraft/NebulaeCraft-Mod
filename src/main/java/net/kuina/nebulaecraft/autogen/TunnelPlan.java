package net.kuina.nebulaecraft.autogen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public final class TunnelPlan {
    public final int dimension;
    public final List<Operation> operations;
    public final List<BlockPos> route;
    public final List<PreviewFrame> previewFrames;
    public final double length;
    public final double minimumRadius;
    public final double maximumGrade;
    public final String preset;
    public final String platformColor;
    public final String power;
    public final boolean mirrored;

    public TunnelPlan(int dimension, List<Operation> operations, List<BlockPos> route,
                      List<PreviewFrame> previewFrames, double length, double minimumRadius,
                      double maximumGrade, String preset, String platformColor, String power,
                      boolean mirrored) {
        this.dimension = dimension;
        this.operations = Collections.unmodifiableList(operations);
        this.route = Collections.unmodifiableList(route);
        this.previewFrames = Collections.unmodifiableList(previewFrames);
        this.length = length;
        this.minimumRadius = minimumRadius;
        this.maximumGrade = maximumGrade;
        this.preset = preset;
        this.platformColor = platformColor;
        this.power = power;
        this.mirrored = mirrored;
    }

    public static final class PreviewFrame {
        public final Vec3d leftBottom;
        public final Vec3d leftTop;
        public final Vec3d rightTop;
        public final Vec3d rightBottom;
        public final boolean ring;

        public PreviewFrame(Vec3d leftBottom, Vec3d leftTop, Vec3d rightTop,
                            Vec3d rightBottom, boolean ring) {
            this.leftBottom = leftBottom;
            this.leftTop = leftTop;
            this.rightTop = rightTop;
            this.rightBottom = rightBottom;
            this.ring = ring;
        }
    }

    public static final class Operation {
        public final BlockPos pos;
        public final IBlockState state;
        public final boolean captureUndo;

        public Operation(BlockPos pos, IBlockState state) {
            this(pos, state, true);
        }

        public Operation(BlockPos pos, IBlockState state, boolean captureUndo) {
            this.pos = pos;
            this.state = state;
            this.captureUndo = captureUndo;
        }
    }
}
