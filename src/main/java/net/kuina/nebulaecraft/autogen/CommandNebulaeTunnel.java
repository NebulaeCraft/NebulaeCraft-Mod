package net.kuina.nebulaecraft.autogen;

import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CommandNebulaeTunnel extends CommandBase {
    @Override
    public String getName() {
        return "nebulaetunnel";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("ntunnel", "nautogen");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ntunnel preview <template> <template arguments>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return AutogenPermissions.FALLBACK_OP_LEVEL;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return AutogenPermissions.canUse(sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 0) {
            throw new CommandException(getUsage(sender));
        }
        String action = args[0].toLowerCase(Locale.ENGLISH);
        switch (action) {
            case "preview":
                preview(player, args);
                return;
            case "confirm":
                player.sendMessage(new TextComponentString(callConfirm(player)));
                return;
            case "cancel":
                player.sendMessage(new TextComponentString(callCancel(player)));
                return;
            case "status":
                player.sendMessage(new TextComponentString(callStatus(player)));
                return;
            case "undo":
                player.sendMessage(new TextComponentString(callUndo(player)));
                return;
            case "clear":
                player.sendMessage(new TextComponentString(callClear(player)));
                return;
            default:
                throw new CommandException(getUsage(sender));
        }
    }

    private void preview(EntityPlayerMP player, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new CommandException(getUsage(player));
        }
        String[] templateArguments = Arrays.copyOfRange(args, 2, args.length);
        try {
            player.sendMessage(new TextComponentString(
                    AutogenService.preview(player, args[1], templateArguments)));
        } catch (AutogenBuildException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static String callConfirm(EntityPlayerMP player) throws CommandException {
        try {
            return AutogenService.confirm(player);
        } catch (AutogenBuildException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static String callCancel(EntityPlayerMP player) throws CommandException {
        try {
            return AutogenService.cancel(player);
        } catch (AutogenBuildException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static String callStatus(EntityPlayerMP player) throws CommandException {
        try {
            return AutogenService.status(player);
        } catch (AutogenBuildException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static String callUndo(EntityPlayerMP player) throws CommandException {
        try {
            return AutogenService.undo(player);
        } catch (AutogenBuildException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static String callClear(EntityPlayerMP player) throws CommandException {
        try {
            return AutogenService.clear(player);
        } catch (AutogenBuildException e) {
            throw new CommandException(e.getMessage());
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "preview", "confirm", "cancel", "status", "undo", "clear");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            return getListOfStringsMatchingLastWord(args, AutogenTemplateRegistry.getIds());
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("preview")) {
            AutogenTemplate template = AutogenTemplateRegistry.get(args[1]);
            if (template != null) {
                String[] templateArguments = Arrays.copyOfRange(args, 2, args.length);
                return getListOfStringsMatchingLastWord(
                        args, template.getTabCompletions(templateArguments));
            }
        }
        return Collections.emptyList();
    }
}
