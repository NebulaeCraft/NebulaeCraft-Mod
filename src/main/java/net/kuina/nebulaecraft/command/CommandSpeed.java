package net.kuina.nebulaecraft.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandSpeed extends CommandBase {
    private static final float DEFAULT_FLY_SPEED = 0.05F;
    private static final float DEFAULT_WALK_SPEED = 0.1F;

    @Override
    public String getName() {
        return "speed";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/speed <倍速>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return server.isSinglePlayer() && sender instanceof EntityPlayerMP;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length != 1) {
            throw new CommandException(getUsage(sender));
        }

        final double multiplier;
        try {
            multiplier = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            throw new CommandException("倍速必须是非负数。");
        }
        if (!Double.isFinite(multiplier) || multiplier < 0) {
            throw new CommandException("倍速必须是有限的非负数。");
        }

        boolean flying = player.capabilities.isFlying;
        double speed = multiplier * (flying ? DEFAULT_FLY_SPEED : DEFAULT_WALK_SPEED);
        if (speed > Float.MAX_VALUE || (multiplier > 0 && (float) speed == 0)) {
            throw new CommandException("倍速超出可用范围。");
        }

        // The capability setters are client-only in 1.12.2. NBT keeps the other abilities intact.
        NBTTagCompound data = new NBTTagCompound();
        player.capabilities.writeCapabilitiesToNBT(data);
        data.getCompoundTag("abilities").setFloat(flying ? "flySpeed" : "walkSpeed", (float) speed);
        player.capabilities.readCapabilitiesFromNBT(data);
        player.sendPlayerAbilities();
        player.sendMessage(new TextComponentString((flying ? "飞行" : "行走") + "速度已设为默认的 " + args[0] + " 倍。"));
    }
}
