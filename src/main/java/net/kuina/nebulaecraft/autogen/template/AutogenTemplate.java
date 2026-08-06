package net.kuina.nebulaecraft.autogen.template;

import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * Public extension point for automatic linear structures.
 *
 * <p>Every tunnel or bridge owns its command arguments and converts a shared marker selection into
 * an {@link AutogenPlan}. Implementations may reuse the route package without depending on the
 * existing metro tunnel section algorithm.</p>
 */
public interface AutogenTemplate {
    String getId();

    String getDisplayName();

    AutogenTemplateKind getKind();

    /** Usage fragment following the template id. */
    String getUsage();

    /** Suggestions for arguments following the template id, including the current partial word. */
    List<String> getTabCompletions(String[] arguments);

    AutogenPlan build(WorldServer world, AutogenSelection.Selection selection, String[] arguments)
            throws AutogenBuildException;
}
