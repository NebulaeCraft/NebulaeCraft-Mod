package net.kuina.nebulaecraft.network;

import io.netty.buffer.ByteBuf;
import net.kuina.nebulaecraft.entities.CameraCart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 → 服务端：乘坐拍摄用车的玩家按下了 Railcraft 的机车按键。
 * action 取值见 {@link CameraCart.EntityCameraCart} 的 ACTION_* 常量。
 */
public class PacketCameraCartControl implements IMessage {
    public int action;

    public PacketCameraCartControl() {}

    public PacketCameraCartControl(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.action);
    }

    public static class Handler implements IMessageHandler<PacketCameraCartControl, IMessage> {
        @Override
        public IMessage onMessage(PacketCameraCartControl message, MessageContext ctx) {
            if (message.action < 0 || message.action >= CameraCart.EntityCameraCart.ACTION_COUNT) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                // 只允许控制自己正在乘坐的那一辆车。
                Entity riding = player.getRidingEntity();
                if (riding instanceof CameraCart.EntityCameraCart) {
                    ((CameraCart.EntityCameraCart) riding).handleControl(message.action);
                }
            });
            return null;
        }
    }
}
