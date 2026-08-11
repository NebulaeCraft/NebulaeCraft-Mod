package net.kuina.nebulaecraft.autogen;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentTranslation;

/** Shared permission gate for commands, the wand UI, and every client request. */
public final class AutogenPermissions {
    public static final String USE = "nebulaecraft.autogen";
    public static final int FALLBACK_OP_LEVEL = 2;

    private AutogenPermissions() {
    }

    /**
     * Vanilla Forge uses the numeric OP level. SpongeForge treats the second argument as a
     * permission node, allowing Sponge LuckPerms to answer the same check without a hard
     * dependency on SpongeAPI or LuckPerms.
     */
    public static boolean canUse(ICommandSender sender) {
        return sender != null && sender.canUseCommand(FALLBACK_OP_LEVEL, USE);
    }

    public static void sendDenied(ICommandSender sender) {
        sender.sendMessage(new TextComponentTranslation(
                "message.nebulaecraft.autogen.permission_denied", USE));
    }
}
