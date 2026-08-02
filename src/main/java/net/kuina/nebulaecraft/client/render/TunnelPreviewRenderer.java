package net.kuina.nebulaecraft.client.render;

import net.kuina.nebulaecraft.network.PacketTunnelPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;

public final class TunnelPreviewRenderer {
    public static final TunnelPreviewRenderer INSTANCE = new TunnelPreviewRenderer();

    private List<PacketTunnelPreview.Frame> frames = Collections.emptyList();
    private int dimension;
    private long expiresAt;

    private TunnelPreviewRenderer() {
    }

    public void handle(PacketTunnelPreview message) {
        if (!message.visible) {
            clear();
            return;
        }
        frames = message.frames;
        dimension = message.dimension;
        expiresAt = System.currentTimeMillis() + Math.max(0, message.lifetimeMillis);
    }

    public void clear() {
        frames = Collections.emptyList();
        expiresAt = 0;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (frames.isEmpty() || minecraft.world == null
                || minecraft.world.provider.getDimension() != dimension) {
            return;
        }
        if (System.currentTimeMillis() >= expiresAt) {
            clear();
            return;
        }
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) {
            return;
        }
        float partialTicks = event.getPartialTicks();
        double cameraX = camera.lastTickPosX
                + (camera.posX - camera.lastTickPosX) * partialTicks;
        double cameraY = camera.lastTickPosY
                + (camera.posY - camera.lastTickPosY) * partialTicks;
        double cameraZ = camera.lastTickPosZ
                + (camera.posZ - camera.lastTickPosZ) * partialTicks;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);

            draw(GL11.GL_GEQUAL, 2.0F, 0.08F, 0.48F, 0.62F, 0.22F,
                    cameraX, cameraY, cameraZ);
            draw(GL11.GL_LEQUAL, 3.0F, 0.10F, 0.90F, 1.00F, 0.92F,
                    cameraX, cameraY, cameraZ);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void draw(int depthFunction, float width, float red, float green,
                      float blue, float alpha, double cameraX, double cameraY,
                      double cameraZ) {
        GL11.glDepthFunc(depthFunction);
        GL11.glLineWidth(width);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 1; i < frames.size(); i++) {
            PacketTunnelPreview.Frame previous = frames.get(i - 1);
            PacketTunnelPreview.Frame current = frames.get(i);
            line(buffer, previous.leftBottom, current.leftBottom, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
            line(buffer, previous.leftTop, current.leftTop, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
            line(buffer, previous.rightTop, current.rightTop, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
            line(buffer, previous.rightBottom, current.rightBottom, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
        }
        for (PacketTunnelPreview.Frame frame : frames) {
            if (!frame.ring) {
                continue;
            }
            line(buffer, frame.leftBottom, frame.leftTop, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
            line(buffer, frame.leftTop, frame.rightTop, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
            line(buffer, frame.rightTop, frame.rightBottom, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
            line(buffer, frame.rightBottom, frame.leftBottom, red, green, blue, alpha,
                    cameraX, cameraY, cameraZ);
        }
        tessellator.draw();
    }

    private static void line(BufferBuilder buffer, Vec3d from, Vec3d to,
                             float red, float green, float blue, float alpha,
                             double cameraX, double cameraY, double cameraZ) {
        vertex(buffer, from, red, green, blue, alpha, cameraX, cameraY, cameraZ);
        vertex(buffer, to, red, green, blue, alpha, cameraX, cameraY, cameraZ);
    }

    private static void vertex(BufferBuilder buffer, Vec3d point,
                               float red, float green, float blue, float alpha,
                               double cameraX, double cameraY, double cameraZ) {
        buffer.pos(point.x - cameraX, point.y - cameraY, point.z - cameraZ)
                .color(red, green, blue, alpha).endVertex();
    }
}
