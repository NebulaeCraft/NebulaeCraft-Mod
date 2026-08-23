package net.kuina.nebulaecraft.autogen.template.bridge;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.List;

/** Built-in single-track metro bridge sampled from the Hello World reference unit. */
public final class MetroBridge1Template implements AutogenTemplate {
    public static final String ID = "bridge_metro_1";
    private static final String DISPLAY_NAME = "地铁桥梁 1";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public AutogenTemplateKind getKind() {
        return AutogenTemplateKind.BRIDGE;
    }

    @Override
    public List<AutogenParameter> getParameters() {
        return Collections.emptyList();
    }

    @Override
    public String getUsage() {
        return "(无参数)";
    }

    @Override
    public List<String> getTabCompletions(String[] arguments) {
        return Collections.emptyList();
    }

    @Override
    public AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                             String[] arguments) throws AutogenBuildException {
        if (arguments.length != 0) {
            throw new AutogenBuildException("模板 " + ID + " 不接受参数");
        }
        return MetroBridge1Builder.build(world, selection, ID, DISPLAY_NAME);
    }
}
