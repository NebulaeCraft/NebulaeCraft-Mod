package net.kuina.nebulaecraft.autogen;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
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
        return Collections.singletonList("ntunnel");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ntunnel preview <preset> <platformColor> <none|catenary|thirdrail_white|thirdrail_yellow> [mirror]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
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
                player.sendMessage(new TextComponentString(TunnelGenerationManager.INSTANCE.confirm(player)));
                return;
            case "cancel":
                player.sendMessage(new TextComponentString(TunnelGenerationManager.INSTANCE.cancel(player)));
                return;
            case "status":
                player.sendMessage(new TextComponentString(TunnelGenerationManager.INSTANCE.status(player)));
                return;
            case "undo":
                player.sendMessage(new TextComponentString(TunnelGenerationManager.INSTANCE.undo(player)));
                return;
            case "clear":
                AutogenSelection.clear(player);
                player.sendMessage(new TextComponentString("自动生成标记选区已清除"));
                return;
            default:
                throw new CommandException(getUsage(sender));
        }
    }

    private void preview(EntityPlayerMP player, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new CommandException(getUsage(player));
        }
        String preset = args[1];
        String color = args[2];
        String power = args[3];
        boolean mirrored = args.length >= 5 && args[4].equalsIgnoreCase("mirror");
        try {
            TunnelPlan plan = TunnelBuilder.build(player.getServerWorld(), AutogenSelection.get(player), preset, color, power, mirrored);
            TunnelGenerationManager.INSTANCE.setPreview(player, plan);
            String radius = Double.isInfinite(plan.minimumRadius) ? "∞" : String.format(Locale.ROOT, "%.2f", plan.minimumRadius);
            player.sendMessage(new TextComponentString(String.format(Locale.ROOT,
                    "预览完成：长度 %.1f，修改 %d 方块，最小半径 %s，最大坡度 %.2f%%，%d 秒内执行 /ntunnel confirm",
                    plan.length, plan.operations.size(), radius, plan.maximumGrade * 100.0,
                    TunnelConfig.get().settings.previewSeconds)));
        } catch (TunnelBuildException e) {
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
            return getListOfStringsMatchingLastWord(args, TunnelConfig.get().presets.keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("preview")) {
            return getListOfStringsMatchingLastWord(args, TunnelBuilder.colorNames());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("preview")) {
            return getListOfStringsMatchingLastWord(args, "none", "catenary", "thirdrail_white", "thirdrail_yellow");
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("preview")) {
            return getListOfStringsMatchingLastWord(args, "mirror");
        }
        return Collections.emptyList();
    }
}
