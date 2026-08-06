package net.kuina.nebulaecraft.network;

import io.netty.buffer.ByteBuf;
import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.autogen.AutogenPlan;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PacketTunnelPreview implements IMessage {
    private static final int MAX_FRAMES = 4096;

    public boolean visible;
    public int dimension;
    public int lifetimeMillis;
    public List<Frame> frames = Collections.emptyList();

    public PacketTunnelPreview() {
    }

    private PacketTunnelPreview(boolean visible, int dimension, int lifetimeMillis,
                                List<Frame> frames) {
        this.visible = visible;
        this.dimension = dimension;
        this.lifetimeMillis = lifetimeMillis;
        this.frames = frames;
    }

    public static PacketTunnelPreview show(AutogenPlan plan, int lifetimeMillis) {
        List<Frame> frames = new ArrayList<>(plan.previewFrames.size());
        for (AutogenPlan.PreviewFrame frame : plan.previewFrames) {
            frames.add(new Frame(frame.leftBottom, frame.leftTop, frame.rightTop,
                    frame.rightBottom, frame.ring));
        }
        return new PacketTunnelPreview(true, plan.dimension, lifetimeMillis, frames);
    }

    public static PacketTunnelPreview clear() {
        return new PacketTunnelPreview(false, 0, 0, Collections.<Frame>emptyList());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        visible = buf.readBoolean();
        if (!visible) {
            frames = Collections.emptyList();
            return;
        }
        dimension = buf.readInt();
        lifetimeMillis = buf.readInt();
        int originX = buf.readInt();
        int originY = buf.readInt();
        int originZ = buf.readInt();
        int count = buf.readInt();
        if (count < 0 || count > MAX_FRAMES) {
            throw new IllegalArgumentException("Invalid tunnel preview frame count: " + count);
        }
        List<Frame> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vec3d leftBottom = readPoint(buf, originX, originY, originZ);
            Vec3d leftTop = readPoint(buf, originX, originY, originZ);
            Vec3d rightTop = readPoint(buf, originX, originY, originZ);
            Vec3d rightBottom = readPoint(buf, originX, originY, originZ);
            decoded.add(new Frame(leftBottom, leftTop, rightTop, rightBottom,
                    buf.readBoolean()));
        }
        frames = Collections.unmodifiableList(decoded);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(visible);
        if (!visible) {
            return;
        }
        buf.writeInt(dimension);
        buf.writeInt(lifetimeMillis);
        Vec3d origin = frames.isEmpty() ? Vec3d.ZERO : frames.get(0).leftBottom;
        int originX = (int) Math.floor(origin.x);
        int originY = (int) Math.floor(origin.y);
        int originZ = (int) Math.floor(origin.z);
        buf.writeInt(originX);
        buf.writeInt(originY);
        buf.writeInt(originZ);
        buf.writeInt(frames.size());
        for (Frame frame : frames) {
            writePoint(buf, frame.leftBottom, originX, originY, originZ);
            writePoint(buf, frame.leftTop, originX, originY, originZ);
            writePoint(buf, frame.rightTop, originX, originY, originZ);
            writePoint(buf, frame.rightBottom, originX, originY, originZ);
            buf.writeBoolean(frame.ring);
        }
    }

    private static Vec3d readPoint(ByteBuf buf, int originX, int originY, int originZ) {
        return new Vec3d(originX + buf.readFloat(), originY + buf.readFloat(),
                originZ + buf.readFloat());
    }

    private static void writePoint(ByteBuf buf, Vec3d point,
                                   int originX, int originY, int originZ) {
        buf.writeFloat((float) (point.x - originX));
        buf.writeFloat((float) (point.y - originY));
        buf.writeFloat((float) (point.z - originZ));
    }

    public static final class Frame {
        public final Vec3d leftBottom;
        public final Vec3d leftTop;
        public final Vec3d rightTop;
        public final Vec3d rightBottom;
        public final boolean ring;

        Frame(Vec3d leftBottom, Vec3d leftTop, Vec3d rightTop,
              Vec3d rightBottom, boolean ring) {
            this.leftBottom = leftBottom;
            this.leftTop = leftTop;
            this.rightTop = rightTop;
            this.rightBottom = rightBottom;
            this.ring = ring;
        }
    }

    public static final class Handler implements IMessageHandler<PacketTunnelPreview, IMessage> {
        @Override
        public IMessage onMessage(PacketTunnelPreview message, MessageContext context) {
            NebulaecraftMod.proxy.handleTunnelPreview(message);
            return null;
        }
    }
}
