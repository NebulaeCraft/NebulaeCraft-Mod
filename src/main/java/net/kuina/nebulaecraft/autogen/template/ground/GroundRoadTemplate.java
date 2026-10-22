package net.kuina.nebulaecraft.autogen.template.ground;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.minecraft.world.WorldServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Shared command and wand controls for surface-only road templates. */
abstract class GroundRoadTemplate implements AutogenTemplate {
    private static final List<String> DASH_PATTERNS =
            Collections.unmodifiableList(Arrays.asList("2+2", "3+3"));
    private static final List<AutogenParameter> DASH_PARAMETERS = Collections.singletonList(
            AutogenParameter.choice("dash_pattern", "gui.nebulaecraft.autogen.dash_pattern",
                    "2+2", DASH_PATTERNS));

    private final String id;
    private final String displayName;
    private final GroundRoadProfile profile;

    GroundRoadTemplate(String id, String displayName, GroundRoadProfile profile) {
        this.id = id;
        this.displayName = displayName;
        this.profile = profile;
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
        return profile.hasDashedLines() ? DASH_PARAMETERS : Collections.emptyList();
    }

    @Override
    public final String getUsage() {
        return profile.hasDashedLines() ? "[dashPattern: 2+2|3+3]" : "";
    }

    @Override
    public final List<String> getTabCompletions(String[] arguments) {
        return profile.hasDashedLines() && arguments.length == 1
                ? DASH_PATTERNS : Collections.emptyList();
    }

    @Override
    public final AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                                   String[] arguments) throws AutogenBuildException {
        if (!profile.hasDashedLines() && arguments.length != 0) {
            throw new AutogenBuildException("模板 " + id + " 不接受额外参数");
        }
        if (arguments.length > 1 || arguments.length == 1 && !DASH_PATTERNS.contains(arguments[0])) {
            throw new AutogenBuildException("模板 " + id + " 的虚线实空组合只能为 2+2 或 3+3");
        }
        int dashLength = arguments.length == 0 || arguments[0].equals("2+2") ? 2 : 3;
        return GroundRoadBuilder.build(world, selection, id, displayName, profile, dashLength);
    }
}
