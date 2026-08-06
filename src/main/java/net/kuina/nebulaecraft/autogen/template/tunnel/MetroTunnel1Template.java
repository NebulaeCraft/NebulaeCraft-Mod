package net.kuina.nebulaecraft.autogen.template.tunnel;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.TunnelBuildException;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateKind;
import net.minecraft.world.WorldServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Complete module for the original seven-by-seven metro tunnel implementation. */
public final class MetroTunnel1Template implements AutogenTemplate {
    public static final String ID = "tunnel_metro_1";
    private static final String DISPLAY_NAME = "地铁隧道 1";
    private static final TunnelTemplateProfile PROFILE = new TunnelTemplateProfile(
            5,
            4,
            1,
            12.0,
            0.1,
            "railcraft:reinforced_concrete@8",
            "nebulaecraft:reinforced_concrete_slab@1",
            "nebulaecraft:reinforced_concrete_vertical_slab@0",
            "minecraft:double_stone_slab@8",
            "minecraft:stone_slab@0",
            "railcraft:track_flex_reinforced@0",
            "railcraft:post_metal_platform@0");

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
    public String getUsage() {
        return "<platformColor> <none|catenary|thirdrail_white|thirdrail_yellow> [mirror]";
    }

    @Override
    public List<String> getTabCompletions(String[] arguments) {
        if (arguments.length == 1) {
            return MetroTunnel1Builder.colorNames();
        }
        if (arguments.length == 2) {
            return Arrays.asList("none", "catenary", "thirdrail_white", "thirdrail_yellow");
        }
        if (arguments.length == 3) {
            return Collections.singletonList("mirror");
        }
        return Collections.emptyList();
    }

    @Override
    public AutogenPlan build(WorldServer world, AutogenSelection.Selection selection,
                             String[] arguments) throws AutogenBuildException {
        if (arguments.length < 2 || arguments.length > 3) {
            throw new TunnelBuildException("模板 " + ID + " 参数: " + getUsage());
        }
        boolean mirrored = arguments.length == 3;
        if (mirrored && !arguments[2].equalsIgnoreCase("mirror")) {
            throw new TunnelBuildException("未知模板参数: " + arguments[2]);
        }
        return MetroTunnel1Builder.build(world, selection, ID, DISPLAY_NAME, PROFILE,
                arguments[0], arguments[1], mirrored);
    }
}
