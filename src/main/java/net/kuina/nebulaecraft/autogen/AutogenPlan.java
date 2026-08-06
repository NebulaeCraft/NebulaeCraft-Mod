package net.kuina.nebulaecraft.autogen;

import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A template-independent structure plan consumed by preview, execution, and undo services. */
public class AutogenPlan {
    public final int dimension;
    public final List<Operation> operations;
    public final List<BlockPos> route;
    public final List<PreviewFrame> previewFrames;
    public final double length;
    public final double minimumRadius;
    public final double maximumGrade;
    public final String templateId;
    public final String displayName;
    public final AutogenTemplateKind kind;

    public AutogenPlan(int dimension, List<Operation> operations, List<BlockPos> route,
                       List<PreviewFrame> previewFrames, double length, double minimumRadius,
                       double maximumGrade, String templateId, String displayName,
                       AutogenTemplateKind kind) {
        this.dimension = dimension;
        this.operations = immutableCopy(operations);
        this.route = immutableCopy(route);
        this.previewFrames = immutableCopy(previewFrames);
        this.length = length;
        this.minimumRadius = minimumRadius;
        this.maximumGrade = maximumGrade;
        this.templateId = templateId;
        this.displayName = displayName;
        this.kind = kind;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
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
