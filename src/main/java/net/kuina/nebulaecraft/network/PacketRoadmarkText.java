package net.kuina.nebulaecraft.network;

import io.netty.buffer.ByteBuf;
import net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketRoadmarkText implements IMessage {
    public BlockPos pos;
    public String text;
    public int color;

    public PacketRoadmarkText() {}

    public PacketRoadmarkText(BlockPos pos, String text) {
        this(pos, text, TileEntityRoadmarkText.COLOR_WHITE);
    }

    public PacketRoadmarkText(BlockPos pos, String text, int color) {
        this.pos = pos;
        this.text = text;
        this.color = color;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.text = ByteBufUtils.readUTF8String(buf);
        this.color = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        ByteBufUtils.writeUTF8String(buf, this.text);
        buf.writeInt(this.color);
    }

    public static class Handler implements IMessageHandler<PacketRoadmarkText, IMessage> {
        @Override
        public IMessage onMessage(PacketRoadmarkText message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                World world = player.world;
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileEntityRoadmarkText) {
                    ((TileEntityRoadmarkText) te).setText(message.text);
                    ((TileEntityRoadmarkText) te).setColor(message.color);
                    // 通知所有客户端更新
                    IBlockState state = world.getBlockState(message.pos);
                    world.notifyBlockUpdate(message.pos, state, state, 3);
                }
            });
            return null;
        }
    }
}
