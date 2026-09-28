package myau.ui.impl.clickgui.element.setting;

import myau.property.properties.ColorProperty;
import myau.ui.impl.clickgui.Theme;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Mouse;

import java.awt.Color;

public class ColorElement extends SettingElement {
    private static final int SV_H = 48;
    private static final int HUE_H = 6;
    private static final int GAP = 4;
    private static final int HEADER_H = Theme.SETTING_H;

    private final ColorProperty prop;
    private boolean draggingSV;
    private boolean draggingHue;
    private float hue;
    private float saturation;
    private float brightness;
    private int cachedColor;

    public ColorElement(ColorProperty prop, int x, int y, int width) {
        super(x, y, width, HEADER_H + GAP + SV_H + GAP + HUE_H + 4);
        this.prop = prop;
        this.cachedColor = prop.getValue();
        updateHSB();
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    private void updateHSB() {
        int color = prop.getValue();
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    private void updateColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        // 保留原本 alpha（若有），否則用不透明
        int alpha = (prop.getValue() >> 24) & 0xFF;
        if (alpha == 0) alpha = 0xFF;
        int finalColor = (alpha << 24) | (rgb & 0x00FFFFFF);
        prop.setValue(finalColor);
        cachedColor = finalColor;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;

        if (!Mouse.isButtonDown(0)) {
            draggingSV = false;
            draggingHue = false;
        }

        if (!draggingSV && !draggingHue && prop.getValue() != cachedColor) {
            cachedColor = prop.getValue();
            updateHSB();
        }

        if (draggingSV) {
            float s = (mouseX - (x + 2f)) / (width - 4f);
            float b = 1.0f - ((mouseY - (y + HEADER_H + GAP)) / (float) SV_H);
            saturation = clamp(s);
            brightness = clamp(b);
            updateColor();
        } else if (draggingHue) {
            float h = (mouseX - (x + 2f)) / (width - 4f);
            hue = clamp(h);
            updateColor();
        }

        int a = (int) (255 * alpha);
        float px = x + 2;
        float pw = width - 4;

        // 名稱 + hex
        String name = prop.getName();
        String hex = String.format("#%06X", prop.getValue() & 0xFFFFFF);
        int tc = Theme.rgba(Theme.TEXT, a);
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(name, x + 2, y + 4, tc);
            float hw = (float) FontManager.productSans16.getStringWidth(hex);
            FontManager.productSans16.drawString(hex, x + width - hw - 18, y + 4, Theme.rgba(Theme.TEXT_DIM, a));
        } else {
            mc.fontRendererObj.drawStringWithShadow(name, x + 2, y + 5, tc);
        }

        // 右側顏色預覽方塊
        int previewColor = (a << 24) | (prop.getValue() & 0x00FFFFFF);
        RenderUtil.drawRoundedRect(x + width - 14, y + 5, 10, 10, 2f, previewColor, true, true, true, true);

        // SV 面板
        float svY = y + HEADER_H + GAP;
        int hueRgb = Color.HSBtoRGB(hue, 1f, 1f);
        RenderUtil.drawRect(px, svY, px + pw, svY + SV_H, hueRgb);
        drawGradientRect(px, svY, pw, SV_H, 0xFFFFFFFF, 0x00FFFFFF, true);
        drawGradientRect(px, svY, pw, SV_H, 0x00000000, 0xFF000000, false);

        // SV 指示點
        float ix = px + saturation * pw;
        float iy = svY + (1f - brightness) * SV_H;
        RenderUtil.drawCircleOutline(ix, iy, 3f, 2.0f, 0xFF000000);
        RenderUtil.drawCircleOutline(ix, iy, 3f, 1.0f, 0xFFFFFFFF);

        // Hue 條
        float hueY = svY + SV_H + GAP;
        drawRainbowRect(px, hueY, pw, HUE_H);
        float hx = px + hue * pw;
        RenderUtil.drawRect(hx - 1, hueY - 1, hx + 1, hueY + HUE_H + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isVisible()) return false;

        float px = x + 2;
        float pw = width - 4;
        float svY = y + HEADER_H + GAP;

        if (mouseX >= px && mouseX <= px + pw && mouseY >= svY && mouseY <= svY + SV_H) {
            draggingSV = true;
            saturation = clamp((mouseX - px) / pw);
            brightness = clamp(1f - (mouseY - svY) / SV_H);
            updateColor();
            return true;
        }

        float hueY = svY + SV_H + GAP;
        if (mouseX >= px && mouseX <= px + pw && mouseY >= hueY && mouseY <= hueY + HUE_H) {
            draggingHue = true;
            hue = clamp((mouseX - px) / pw);
            updateColor();
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        draggingSV = false;
        draggingHue = false;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void drawRainbowRect(float x, float y, float width, float height) {
        int[] colors = {
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        float seg = width / 6f;
        for (int i = 0; i < 6; i++) {
            drawGradientRect(x + i * seg, y, seg, height, colors[i], colors[i + 1], true);
        }
    }

    private void drawGradientRect(float x, float y, float width, float height, int startColor, int endColor, boolean horizontal) {
        float sa = (startColor >> 24 & 255) / 255f;
        float sr = (startColor >> 16 & 255) / 255f;
        float sg = (startColor >> 8 & 255) / 255f;
        float sb = (startColor & 255) / 255f;
        float ea = (endColor >> 24 & 255) / 255f;
        float er = (endColor >> 16 & 255) / 255f;
        float eg = (endColor >> 8 & 255) / 255f;
        float eb = (endColor & 255) / 255f;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        if (horizontal) {
            wr.pos(x + width, y, 0).color(er, eg, eb, ea).endVertex();
            wr.pos(x, y, 0).color(sr, sg, sb, sa).endVertex();
            wr.pos(x, y + height, 0).color(sr, sg, sb, sa).endVertex();
            wr.pos(x + width, y + height, 0).color(er, eg, eb, ea).endVertex();
        } else {
            wr.pos(x + width, y, 0).color(sr, sg, sb, sa).endVertex();
            wr.pos(x, y, 0).color(sr, sg, sb, sa).endVertex();
            wr.pos(x, y + height, 0).color(er, eg, eb, ea).endVertex();
            wr.pos(x + width, y + height, 0).color(er, eg, eb, ea).endVertex();
        }
        tess.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
