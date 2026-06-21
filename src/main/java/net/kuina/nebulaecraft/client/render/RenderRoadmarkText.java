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

    // 【新增】用于在客户端内存中缓存每个坐标的文字和生成的贴图
    private static final java.util.Map<net.minecraft.util.math.BlockPos, String> textCache = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.util.math.BlockPos, Integer> colorCache = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.util.math.BlockPos, DynamicTexture> textureCache = new java.util.HashMap<>();

    @Override
    public void render(TileEntityRoadmarkText te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        String currentText = te.getText();
        if (currentText == null || currentText.isEmpty()) return;
        int currentColor = te.getColor();

        net.minecraft.util.math.BlockPos pos = te.getPos();
        String cachedText = textCache.get(pos);
        Integer cachedColor = colorCache.get(pos);
        DynamicTexture tex = textureCache.get(pos);

        // 如果该坐标还没贴图，或者文字/颜色发生了改变，就重新生成贴图
        if (tex == null || !currentText.equals(cachedText) || cachedColor == null || currentColor != cachedColor) {
            tex = generateTexture(currentText, currentColor);
            // 清理旧内存
            if (textureCache.containsKey(pos)) {
                textureCache.get(pos).deleteGlTexture();
            }
            textureCache.put(pos, tex);
            textCache.put(pos, currentText);
            colorCache.put(pos, currentColor);
        }

        // 渲染部分
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.01D, z + 0.5D);

        EnumFacing facing = te.getWorld().getBlockState(te.getPos()).getValue(BlockRoadmarkText.BlockCustom.FACING);
        float angle = 0;
        if (facing == EnumFacing.NORTH) angle = 180;
        else if (facing == EnumFacing.EAST) angle = 90;
        else if (facing == EnumFacing.WEST) angle = -90;
        GlStateManager.rotate(angle, 0, 1, 0);

        GlStateManager.bindTexture(tex.getGlTextureId()); // 绑定缓存的贴图
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.disableLighting();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        double w = 0.4375; // 14/16 的一半，整体宽 14px
        double h = 0.9375; // 30/16 的一半，整体长 30px
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

    // 生成贴图方法改为接受文字和颜色并返回 DynamicTexture
    private DynamicTexture generateTexture(String text, int color) {
        int width = 180;
        int height = 390;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new java.awt.Color(color));

        java.awt.Font font = trafficaFont.deriveFont(100f);
        g2d.setFont(font);
        // 使用文字的实际像素轮廓边界（visual bounds），而不是包含上伸/下伸空白的逻辑边界，
        // 这样无下伸部的字符（如数字、大写字母）也能在贴图内真正居中，不再偏上。
        java.awt.font.FontRenderContext frc = g2d.getFontRenderContext();
        java.awt.font.GlyphVector gv = font.createGlyphVector(frc, text);
        java.awt.geom.Rectangle2D rect = gv.getVisualBounds();

        double scaleX = width / rect.getWidth();
        double scaleY = height / rect.getHeight();
        g2d.scale(scaleX, scaleY);

        g2d.drawGlyphVector(gv, (float) -rect.getX(), (float) -rect.getY());
        g2d.dispose();

        return new DynamicTexture(image);
    }
}
