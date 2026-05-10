package net.kuina.nebulaecraft.client.render;

import net.kuina.nebulaecraft.block.BlockRoadmarkText;
import net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class RenderRoadmarkText extends TileEntitySpecialRenderer<TileEntityRoadmarkText> {

    private static Font trafficaFont;

    public RenderRoadmarkText() {
        // 初始化时加载你的 ttf 字体
        if (trafficaFont == null) {
            try {
                ResourceLocation fontLoc = new ResourceLocation("nebulaecraft", "fonts/roadmark.ttf");
                InputStream is = Minecraft.getMinecraft().getResourceManager().getResource(fontLoc).getInputStream();
                trafficaFont = Font.createFont(Font.TRUETYPE_FONT, is);
            } catch (Exception e) {
                e.printStackTrace();
                trafficaFont = new Font("SansSerif", Font.BOLD, 100); // 容错备用字体
            }
        }
    }

    @Override
    public void render(TileEntityRoadmarkText te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getText() == null || te.getText().isEmpty()) return;

        // 如果文字更改了，重新绘制贴图
        if (te.needsUpdate || te.texture == null) {
            generateTexture(te);
        }

        if (te.texture != null) {
            GlStateManager.pushMatrix();
            // 移动到方块中心，稍微浮在表面上(0.06高度防止Z-fighting)
            GlStateManager.translate(x + 0.5D, y + 0.01D, z + 0.5D);

            // 读取方块朝向并旋转
            EnumFacing facing = te.getWorld().getBlockState(te.getPos()).getValue(BlockRoadmarkText.BlockCustom.FACING);
            float angle = 0;
            if (facing == EnumFacing.NORTH) angle = 180;
            else if (facing == EnumFacing.EAST) angle = 90;
            else if (facing == EnumFacing.WEST) angle = -90;
            GlStateManager.rotate(angle, 0, 1, 0);

            // 绑定生成的动态贴图
            GlStateManager.bindTexture(te.texture.getGlTextureId());

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            GlStateManager.disableLighting();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            // 根据 1.3x1.7 的尺寸，X半径为 0.65，Z半径为 0.85
            double w = 0.7;
            double h = 1.0;

            buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(-w, 0, -h).tex(0, 0).endVertex();
            buffer.pos(-w, 0, h).tex(0, 1).endVertex();
            buffer.pos(w, 0, h).tex(1, 1).endVertex();
            buffer.pos(w, 0, -h).tex(1, 0).endVertex();
            tessellator.draw();

            GlStateManager.enableLighting();

            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private void generateTexture(TileEntityRoadmarkText te) {
        // 创建一个比例为 1.25 : 1.75 的画布 (相当于 500 x 700 像素)
        int width = 140;
        int height = 200;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // 开启抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置 #F9F9F9 颜色
        g2d.setColor(new Color(249, 249, 249));

        Font font = trafficaFont.deriveFont(100f); // 基础大小
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        Rectangle2D rect = fm.getStringBounds(te.getText(), g2d);

        // --- 核心：拉伸铺展逻辑 ---
        // 计算 X 和 Y 的缩放比例，让文字恰好铺满 500x700 的画板
        double scaleX = width / rect.getWidth();
        double scaleY = height / rect.getHeight();
        g2d.scale(scaleX, scaleY);

        // 绘制文字 (减去 XY 偏移以对齐左上角)
        g2d.drawString(te.getText(), (float) -rect.getX(), (float) -rect.getY());
        g2d.dispose();

        // 清理旧贴图防止内存泄漏，并上传新贴图
        if (te.texture != null) {
            te.texture.deleteGlTexture();
        }
        te.texture = new DynamicTexture(image);
        te.needsUpdate = false;
    }
}