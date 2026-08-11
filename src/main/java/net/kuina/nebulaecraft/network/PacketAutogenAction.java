package net.kuina.nebulaecraft.network;

import io.netty.buffer.ByteBuf;
import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.autogen.AutogenPermissions;
import net.kuina.nebulaecraft.autogen.AutogenService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated client intent; all world-changing work is resolved again on the server. */
public final class PacketAutogenAction implements IMessage {
    public static final int PREVIEW = 0;
    public static final int CONFIRM = 1;
    public static final int CANCEL = 2;
    public static final int UNDO = 3;
    public static final int CLEAR = 4;
    public static final int REFRESH = 5;

    private static final int MAX_ARGUMENTS = 16;
    private static final int MAX_STRING = 64;

    public int action;
    public String templateId = "";
    public List<String> arguments = Collections.emptyList();

    public PacketAutogenAction() {
    }

    public PacketAutogenAction(int action) {
        this(action, "", Collections.<String>emptyList());
    }

    public PacketAutogenAction(int action, String templateId, List<String> arguments) {
        this.action = action;
        this.templateId = templateId == null ? "" : templateId;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
        templateId = readString(buf);
        int count = buf.readUnsignedByte();
        if (count > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("Too many autogen arguments: " + count);
        }
        List<String> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            decoded.add(readString(buf));
        }
        arguments = Collections.unmodifiableList(decoded);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, templateId);
        buf.writeByte(arguments.size());
        for (String argument : arguments) {
            ByteBufUtils.writeUTF8String(buf, argument);
        }
    }

    private static String readString(ByteBuf buf) {
        String value = ByteBufUtils.readUTF8String(buf);
        if (value.length() > MAX_STRING) {
            throw new IllegalArgumentException("Autogen action string is too long");
        }
        return value;
    }

    public static final class Handler implements IMessageHandler<PacketAutogenAction, IMessage> {
        @Override
        public IMessage onMessage(PacketAutogenAction message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(message, player));
            return null;
        }

        private static void handle(PacketAutogenAction message, EntityPlayerMP player) {
            if (!AutogenPermissions.canUse(player)) {
                AutogenPermissions.sendDenied(player);
                return;
            }
            try {
                String result;
                switch (message.action) {
                    case PREVIEW:
                        result = AutogenService.preview(player, message.templateId,
                                message.arguments.toArray(new String[0]));
                        break;
                    case CONFIRM:
                        result = AutogenService.confirm(player);
                        break;
                    case CANCEL:
                        result = AutogenService.cancel(player);
                        break;
                    case UNDO:
                        result = AutogenService.undo(player);
                        break;
                    case CLEAR:
                        result = AutogenService.clear(player);
                        player.sendMessage(new TextComponentString(result));
                        AutogenService.openGui(player);
                        return;
                    case REFRESH:
                        AutogenService.openGui(player);
                        return;
                    default:
                        player.sendMessage(new TextComponentString("未知自动生成界面操作"));
                        return;
                }
                player.sendMessage(new TextComponentString(result));
            } catch (AutogenBuildException e) {
                player.sendMessage(new TextComponentString(e.getMessage()));
            }
        }
    }
}
