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

    private static Font roadmarkFont;   // 完整字体（roadmark.ttf），用于西文字体不包含的字符
    private static Font roadmarkEnFont; // 仅西文字体（roadmark_en.ttf），优先使用

    public RenderRoadmarkText() {
        // 初始化时加载 ttf 字体
        if (roadmarkFont == null) {
            try {
                ResourceLocation fontLoc = new ResourceLocation("nebulaecraft", "fonts/roadmark.ttf");
                InputStream is = Minecraft.getMinecraft().getResourceManager().getResource(fontLoc).getInputStream();
                roadmarkFont = Font.createFont(Font.TRUETYPE_FONT, is);
            } catch (Exception e) {
                e.printStackTrace();
                roadmarkFont = new Font("SansSerif", Font.BOLD, 100); // 容错备用字体
            }
        }
        // 优先使用的西文字体
        if (roadmarkEnFont == null) {
            try {
                ResourceLocation fontLoc = new ResourceLocation("nebulaecraft", "fonts/roadmark_en.ttf");
                InputStream is = Minecraft.getMinecraft().getResourceManager().getResource(fontLoc).getInputStream();
                roadmarkEnFont = Font.createFont(Font.TRUETYPE_FONT, is);
            } catch (Exception e) {
                e.printStackTrace();
                roadmarkEnFont = null; // 加载失败时回退到 roadmarkFont
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
        // 单字符宽 20px；2 个及以上字符（空格也算）整体宽 28px。长度固定 30px。
        double w = (currentText.length() >= 2) ? 0.875 : 0.625; // 半宽：28/16 或 20/16
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

        java.awt.Font enFont = (roadmarkEnFont != null) ? roadmarkEnFont.deriveFont(100f) : null;
        java.awt.Font cnFont = roadmarkFont.deriveFont(100f);

        // 逐字符选择字体：优先使用西文字体 roadmark_en.ttf；当该字体不包含某字符时，
        // 退回使用完整字体 roadmark.ttf。空格等无墨字符按 canDisplay 同样处理。
        java.text.AttributedString as = new java.text.AttributedString(text);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            java.awt.Font use = (enFont != null && enFont.canDisplay(c)) ? enFont : cnFont;
            as.addAttribute(java.awt.font.TextAttribute.FONT, use, i, i + 1);
        }

        java.awt.font.FontRenderContext frc = g2d.getFontRenderContext();
        java.awt.font.TextLayout layout = new java.awt.font.TextLayout(as.getIterator(), frc);
        // 水平方向用 advance（含空格等无墨字符的步进），让空格也占据宽度、正常显示；
        // 垂直方向用实际像素边界（visual bounds），避免无下伸部字符（数字/大写）偏上。
        java.awt.geom.Rectangle2D visual = layout.getBounds();
        double advance = layout.getAdvance();

        double scaleX = width / advance;
        double scaleY = height / visual.getHeight();
        g2d.scale(scaleX, scaleY);

        layout.draw(g2d, 0f, (float) -visual.getY());
        g2d.dispose();

        return new DynamicTexture(image);
    }
}
