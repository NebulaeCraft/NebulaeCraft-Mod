package net.kuina.nebulaecraft.autogen.template.ground;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Shared command and wand controls for ground and bridge variants of each road. */
abstract class GroundRoadTemplate implements AutogenTemplate {
    private static final List<String> DASH_PATTERNS =
            Collections.unmodifiableList(Arrays.asList("2+2", "3+3"));
    private static final List<String> ROAD_STRUCTURES =
            Collections.unmodifiableList(Arrays.asList("ground", "bridge"));
    private static final AutogenParameter STRUCTURE_PARAMETER = AutogenParameter.choice(
            "road_structure", "gui.nebulaecraft.autogen.road_structure", "ground", ROAD_STRUCTURES);
    private static final AutogenParameter MEDIAN_PARAMETER = AutogenParameter.flag(
            "road_median", "gui.nebulaecraft.autogen.road_median", "median");

    private final String id;
    private final String displayName;
    private final GroundRoadProfile profile;
    private final List<AutogenParameter> parameters;

    GroundRoadTemplate(String id, String displayName, GroundRoadProfile profile) {
        this.id = id;
        this.displayName = displayName;
        this.profile = profile;
        List<AutogenParameter> parameters = new ArrayList<>();
        if (profile.hasDashedLines()) {
            parameters.add(AutogenParameter.choice("dash_pattern",
                    "gui.nebulaecraft.autogen.dash_pattern", "2+2", DASH_PATTERNS));
        }
        parameters.add(STRUCTURE_PARAMETER);
        if (profile.twoWay) {
            parameters.add(MEDIAN_PARAMETER);
        }
        this.parameters = Collections.unmodifiableList(parameters);
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final String getDisplayName() {
        return displayName;
    }

    @Override
    public final AutogenTemplateKind getKind() {
        return AutogenTemplateKind.GROUND;
    }

    @Override
    public final List<AutogenParameter> getParameters() {
        return parameters;
    }

    @Override
    public final String getUsage() {
        return (profile.hasDashedLines() ? "[dashPattern: 2+2|3+3] " : "")
                + "[roadStructure: ground|bridge]" + (profile.twoWay ? " [median]" : "");
    }

    @Override
    public final List<String> getTabCompletions(String[] arguments) {
        List<String> choices = new ArrayList<>();
        int next = 0;
        int complete = arguments.length - 1;
        if (complete < 0) {
            return choices;
        }
        if (profile.hasDashedLines()) {
            if (complete == 0) {
                choices.addAll(DASH_PATTERNS);
            } else if (DASH_PATTERNS.contains(arguments[next])) {
                next++;
            }
        }
        if (next == complete) {
            choices.addAll(ROAD_STRUCTURES);
        } else if (ROAD_STRUCTURES.contains(arguments[next])) {
            next++;
        } else {
            return Collections.emptyList();
        }
        if (profile.twoWay && next == complete) {
            choices.add("median");
        }
        return choices;
    }

    @Override
    public final AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                                   String[] arguments) throws AutogenBuildException {
        int next = 0;
        int dashLength = 2;
        if (profile.hasDashedLines() && arguments.length > 0
                && DASH_PATTERNS.contains(arguments[0])) {
            dashLength = arguments[next++].equals("2+2") ? 2 : 3;
        }
        String structure = "ground";
        if (next < arguments.length && ROAD_STRUCTURES.contains(arguments[next])) {
            structure = arguments[next++];
        }
        boolean median = profile.twoWay && next < arguments.length
                && arguments[next].equals("median");
        if (median) {
            next++;
        }
        if (next != arguments.length) {
            throw new AutogenBuildException("模板 " + id + " 的参数格式为 " + getUsage());
        }
        return GroundRoadBuilder.build(world, selection, id, displayName,
                median ? profile.withMedian() : profile, dashLength,
                structure.equals("bridge"));
    }
}
