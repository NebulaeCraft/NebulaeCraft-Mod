package net.kuina.nebulaecraft.autogen.template.tunnel;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.TunnelBuildException;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.List;

/** Built-in double-track metro tunnel sampled from the Hello World reference section. */
public final class MetroTunnel2Template implements AutogenTemplate {
    public static final String ID = "tunnel_metro_2";
    private static final String DISPLAY_NAME = "地铁隧道 2";

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
        return AutogenTemplateKind.TUNNEL;
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
            throw new TunnelBuildException("模板 " + ID + " 不接受参数");
        }
        return MetroTunnel2Builder.build(world, selection, ID, DISPLAY_NAME);
    }
}
