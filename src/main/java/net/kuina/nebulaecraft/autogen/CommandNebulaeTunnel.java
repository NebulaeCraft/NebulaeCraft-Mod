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
        if (args.length < 2) {
            throw new CommandException(getUsage(player));
        }
        AutogenTemplate template = AutogenTemplateRegistry.get(args[1]);
        if (template == null) {
            throw new CommandException("未知自动生成模板: " + args[1]);
        }
        String[] templateArguments = Arrays.copyOfRange(args, 2, args.length);
        try {
            AutogenPlan plan = template.build(
                    player.getServerWorld(), AutogenSelection.get(player), templateArguments);
            AutogenPlanValidator.validate(player.getServerWorld(), plan);
            TunnelGenerationManager.INSTANCE.setPreview(player, plan);
            String radius = Double.isInfinite(plan.minimumRadius) ? "∞" : String.format(Locale.ROOT, "%.2f", plan.minimumRadius);
            player.sendMessage(new TextComponentString(String.format(Locale.ROOT,
                    "%s 预览完成：长度 %.1f，修改 %d 方块，最小半径 %s，最大坡度 %.2f%%，%d 秒内执行 /ntunnel confirm",
                    plan.displayName, plan.length, plan.operations.size(), radius,
                    plan.maximumGrade * 100.0, AutogenConfig.get().previewSeconds)));
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
