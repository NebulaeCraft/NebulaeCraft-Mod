package net.kuina.nebulaecraft.autogen;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Orders final template states for visible construction without changing overlap priorities. */
public final class AutogenBuildOrder {
    public enum Stage {
        CLEARANCE,
        CONCRETE,
        TRACKBED,
        TRACKS,
        SURFACE,
        FIXTURES,
        CATENARY
    }

    private final Stage defaultStage;
    private final Map<Block, Stage> materials = new HashMap<>();

    public AutogenBuildOrder(Stage defaultStage) {
        this.defaultStage = defaultStage;
    }

    public AutogenBuildOrder materials(Stage stage, Block... blocks) {
        for (Block block : blocks) {
            materials.put(block, stage);
        }
        return this;
    }

    public void sort(List<AutogenPlan.Operation> operations, List<BlockPos> route) {
        // Every height in a column shares a route section. Resolve it once, outside the sort.
        Map<Long, Integer> sections = new HashMap<>();
        List<OrderedOperation> ordered = new ArrayList<>(operations.size());
        for (AutogenPlan.Operation operation : operations) {
            long column = ((long) operation.pos.getX() << 32)
                    ^ (operation.pos.getZ() & 0xffffffffL);
            Integer section = sections.get(column);
            if (section == null) {
                section = nearestSection(operation.pos, route);
                sections.put(column, section);
            }
            Stage stage = operation.state.getMaterial() == Material.AIR
                    ? Stage.CLEARANCE
                    : materials.getOrDefault(operation.state.getBlock(), defaultStage);
            ordered.add(new OrderedOperation(operation, stage, section));
        }
        ordered.sort(Comparator
                .comparingInt((OrderedOperation value) -> value.stage.ordinal())
                // Reapply Railcraft shapes after all tracks exist, before adding fixtures.
                .thenComparingInt(value -> value.operation.captureUndo ? 0 : 1)
                // Finish each concrete layer along the whole route before starting the next.
                .thenComparingInt(value -> value.stage == Stage.CONCRETE
                        ? value.operation.pos.getY() : 0)
                .thenComparingInt(value -> value.section)
                .thenComparingInt(value -> value.operation.pos.getY())
                .thenComparingInt(value -> value.operation.pos.getX())
                .thenComparingInt(value -> value.operation.pos.getZ()));
        for (int i = 0; i < ordered.size(); i++) {
            operations.set(i, ordered.get(i).operation);
        }
    }

    private static int nearestSection(BlockPos pos, List<BlockPos> route) {
        int nearest = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            BlockPos center = route.get(i);
            long dx = (long) pos.getX() - center.getX();
            long dz = (long) pos.getZ() - center.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                nearest = i;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private static final class OrderedOperation {
        final AutogenPlan.Operation operation;
        final Stage stage;
        final int section;

        OrderedOperation(AutogenPlan.Operation operation, Stage stage, int section) {
            this.operation = operation;
            this.stage = stage;
            this.section = section;
        }
    }
}
