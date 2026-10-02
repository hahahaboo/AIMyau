package myau.util.font.impl;

import myau.util.font.CenterMode;
import myau.util.font.FontResourceManager;
import myau.util.font.IFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class FontRenderer extends CharRenderer implements IFont {

    final CharData[] boldChars = new CharData[256];
    final CharData[] italicChars = new CharData[256];
    final CharData[] boldItalicChars = new CharData[256];
    final int[] colorCode = new int[32];
    final String colorcodeIdentifiers = "0123456789abcdefklmnor";
    DynamicTexture texBold, texItalic, texItalicBold;

    public FontRenderer(Font font) {
        super(font, true, true);
        this.setupMinecraftColorcodes();
        this.setupBoldItalicIDs();
        FontResourceManager.registerFont(this);
    }

    public void destroy() {
        super.destroy();
        if (this.texBold != null) this.texBold.deleteGlTexture();
        if (this.texItalic != null) this.texItalic.deleteGlTexture();
        if (this.texItalicBold != null) this.texItalicBold.deleteGlTexture();
    }

    public void drawString(String text, double x, double y, @NotNull CenterMode centerMode, boolean dropShadow, int color) {
        switch (centerMode) {
            case X:
                this.drawString(text, x - this.getStringWidth(text) / 2, y, color, dropShadow);
                return;
            case Y:
                this.drawString(text, x, y - this.getHeight() / 2, color, dropShadow);
                return;
            case XY:
                this.drawString(text, x - this.getStringWidth(text) / 2, y - this.getHeight() / 2, color, dropShadow);
                return;
            default:
            case NONE:
                this.drawString(text, x, y, color, dropShadow);
        }
    }

    public void drawString(String text, double x, double y, int color, boolean shadow) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());

        if (text == null) return;

        CharData[] currentData = this.charData;
        double alpha = (color >> 24 & 255) / 255.0;
        if (alpha == 0.0) alpha = 1.0;

        final int originalColor = color;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        x = (x - 1) * sr.getScaleFactor();
        y = (y - 3) * sr.getScaleFactor() - 0.2;
        double shadowOffset = sr.getScaleFactor(); // 約等於邏輯座標 +1

        GL11.glPushMatrix();
        GL11.glScaled((double) 1 / sr.getScaleFactor(), 1 / (double) sr.getScaleFactor(), 1 / (double) sr.getScaleFactor());
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(this.tex.getGlTextureId());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.tex.getGlTextureId());

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (character == '§') {
                int colorIndex = 21;
                try {
                    colorIndex = colorcodeIdentifiers.indexOf(Character.toLowerCase(text.charAt(index + 1)));
                } catch (Exception ignored) {
                }

                if (colorIndex < 16) {
                    int mcColor = this.colorCode[colorIndex];
                    red = (mcColor >> 16 & 255) / 255.0F;
                    green = (mcColor >> 8 & 255) / 255.0F;
                    blue = (mcColor & 255) / 255.0F;
                    currentData = this.charData;
                } else if (colorIndex == 21) {
                    // §r 重置回原始傳入顏色
                    red = (originalColor >> 16 & 255) / 255.0F;
                    green = (originalColor >> 8 & 255) / 255.0F;
                    blue = (originalColor & 255) / 255.0F;
                    currentData = this.charData;
                }
                // k/l/m/n/o 等格式碼暫不處理
                ++index;
                continue;
            }

            if (character < currentData.length) {
                // 陰影：使用「當前顏色」的暗版，避免 suffix 全亮重疊
                if (shadow) {
                    GlStateManager.color(red * 0.25F, green * 0.25F, blue * 0.25F, (float) alpha);
                    GlStateManager.bindTexture(this.tex.getGlTextureId());
                    drawLetter(x + shadowOffset, y + shadowOffset, currentData, character);
                }

                // 正文
                GlStateManager.color(red, green, blue, (float) alpha);
                GlStateManager.bindTexture(this.tex.getGlTextureId());
                drawLetter(x, y, currentData, character);
                x += currentData[character].width - 8.3 + this.charOffset;
            } else {
                // ------------------ Unicode 回退逻辑 ------------------
                int currentColor = ((int) (alpha * 255) & 0xFF) << 24
                        | ((int) (red * 255) & 0xFF) << 16
                        | ((int) (green * 255) & 0xFF) << 8
                        | ((int) (blue * 255) & 0xFF);

                GL11.glPopMatrix();

                float logicalX = (float) (x / sr.getScaleFactor());
                float logicalY = (float) (y / sr.getScaleFactor()) + 3.0f;

                if (shadow) {
                    int shadowColor = (currentColor & 0xFCFCFC) >> 2 | currentColor & 0xFF000000;
                    Minecraft.getMinecraft().fontRendererObj.drawString(
                            String.valueOf(character), logicalX + 1.0F, logicalY + 1.0F, shadowColor, false);
                }
                Minecraft.getMinecraft().fontRendererObj.drawString(
                        String.valueOf(character), logicalX, logicalY, currentColor, false);

                GL11.glPushMatrix();
                GL11.glScaled((double) 1 / sr.getScaleFactor(), 1 / (double) sr.getScaleFactor(), 1 / (double) sr.getScaleFactor());

                GlStateManager.color(red, green, blue, (float) alpha);
                GlStateManager.enableBlend();
                GlStateManager.bindTexture(this.tex.getGlTextureId());

                x += Minecraft.getMinecraft().fontRendererObj.getCharWidth(character) * sr.getScaleFactor();
            }
        }

        GlStateManager.disableBlend();
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_DONT_CARE);
        GL11.glPopMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawString(String text, double x, double y, int color) {
        drawString(text, x, y, color, false);
    }

    @Override
    public double width(String text) {
        return getStringWidth(text);
    }

    @Override
    public void drawCenteredString(String text, double x, double y, int color) {
        drawString(text, x, y, CenterMode.X, false, color);
    }

    @Override
    public double height() {
        return getHeight();
    }

    private void drawLetter(double x, double y, CharData[] currentData, char character) {
        GL11.glBegin(4);
        this.drawChar(currentData, character, x, y);
        GL11.glEnd();
    }

    public double getStringWidth(String text) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        if (text == null) return 0;

        double width = 0;
        CharData[] currentData = charData;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (character == '§') {
                index++;
            } else if (character < currentData.length) {
                width += currentData[character].width - 8.3f + charOffset;
            } else {
                width += Minecraft.getMinecraft().fontRendererObj.getCharWidth(character) * sr.getScaleFactor();
            }
        }

        return width / (double) sr.getScaleFactor();
    }

    public double getHeight() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        return (this.fontHeight - 8) / (double) sr.getScaleFactor();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        this.setupBoldItalicIDs();
    }

    @Override
    public void setAntiAlias(boolean antiAlias) {
        super.setAntiAlias(antiAlias);
        this.setupBoldItalicIDs();
    }

    @Override
    public void setFractionalMetrics(boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
        this.setupBoldItalicIDs();
    }

    private void setupBoldItalicIDs() {
        if (this.texBold != null) this.texBold.deleteGlTexture();
        if (this.texItalic != null) this.texItalic.deleteGlTexture();
        if (this.texItalicBold != null) this.texItalicBold.deleteGlTexture();

        this.texBold = this.setupTexture(this.font.deriveFont(Font.BOLD), this.antiAlias, this.fractionalMetrics, this.boldChars);
        this.texItalic = this.setupTexture(this.font.deriveFont(Font.ITALIC), this.antiAlias, this.fractionalMetrics, this.italicChars);
        this.texItalicBold = this.setupTexture(this.font.deriveFont(Font.BOLD | Font.ITALIC), this.antiAlias, this.fractionalMetrics, this.boldItalicChars);
    }

    private void setupMinecraftColorcodes() {
        int index = 0;
        while (index < 32) {
            int noClue = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + noClue;
            int green = (index >> 1 & 1) * 170 + noClue;
            int blue = (index & 1) * 170 + noClue;
            if (index == 6) red += 85;
            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            this.colorCode[index] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
            ++index;
        }
    }
}
