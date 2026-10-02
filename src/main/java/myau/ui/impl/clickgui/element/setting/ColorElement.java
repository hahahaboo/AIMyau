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
    private static final int SV_SIZE = 100;
    private static final int HUE_H = 8;
    private static final int PAD = 6;
    private static final int PREVIEW_SIZE = 10;

    /** 目前開啟的取色器（同時只允許一個） */
    private static ColorElement openPicker;

    private final ColorProperty prop;
    private boolean draggingSV;
    private boolean draggingHue;
    private float hue, saturation, brightness;
    private int cachedColor;

    /** 小窗位置（由 ClickGuiScreen 設定） */
    private int popupX, popupY;

    public ColorElement(ColorProperty prop, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H);
        this.prop = prop;
        this.cachedColor = prop.getValue();
        updateHSB();
    }

    public static ColorElement getOpenPicker() {
        return openPicker;
    }

    public static void closePicker() {
        if (openPicker != null) {
            openPicker.draggingSV = false;
            openPicker.draggingHue = false;
        }
        openPicker = null;
    }

    public static boolean isPickerOpen() {
        return openPicker != null;
    }

    public int getPopupWidth() {
        return PAD * 2 + SV_SIZE;
    }

    public int getPopupHeight() {
        return PAD * 2 + SV_SIZE + 4 + HUE_H;
    }

    public void setPopupPos(int px, int py) {
        this.popupX = px;
        this.popupY = py;
    }

    public boolean isInsidePopup(int mouseX, int mouseY) {
        return mouseX >= popupX && mouseX <= popupX + getPopupWidth()
                && mouseY >= popupY && mouseY <= popupY + getPopupHeight();
    }

    /** 點在 #hex + 色塊區域 */
    private boolean isInsideSwatchArea(int mouseX, int mouseY) {
        // 從 hex 文字左側到色塊右側
        String hex = String.format("#%06X", prop.getValue() & 0xFFFFFF);
        float hexW = FontManager.productSans16 != null
                ? (float) FontManager.productSans16.getStringWidth(hex)
                : mc.fontRendererObj.getStringWidth(hex);
        float left = x + width - hexW - 18;
        float right = x + width;
        return mouseX >= left && mouseX <= right
                && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    private void updateHSB() {
        int color = prop.getValue();
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private void updateColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        int alpha = (prop.getValue() >> 24) & 0xFF;
        if (alpha == 0) alpha = 0xFF;
        int finalColor = (alpha << 24) | (rgb & 0x00FFFFFF);
        prop.setValue(finalColor);
        cachedColor = finalColor;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ─── 列表內精簡列 ───────────────────────────────────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;

        if (openPicker == this) {
            if (!Mouse.isButtonDown(0)) {
                draggingSV = false;
                draggingHue = false;
            }
            if (!draggingSV && !draggingHue && prop.getValue() != cachedColor) {
                cachedColor = prop.getValue();
                updateHSB();
            }
            if (draggingSV) {
                float svX = popupX + PAD;
                float svY = popupY + PAD;
                saturation = clamp((mouseX - svX) / SV_SIZE);
                brightness = clamp(1f - (mouseY - svY) / SV_SIZE);
                updateColor();
            } else if (draggingHue) {
                float hueX = popupX + PAD;
                float hueY = popupY + PAD + SV_SIZE + 4;
                hue = clamp((mouseX - hueX) / SV_SIZE);
                updateColor();
            }
        }

        int a = (int) (255 * alpha);
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

        int previewColor = (a << 24) | (prop.getValue() & 0x00FFFFFF);
        RenderUtil.drawRoundedRect(x + width - 14, y + 5, PREVIEW_SIZE, PREVIEW_SIZE, 2f,
                previewColor, true, true, true, true);
    }

    // ─── 延伸小窗（必須在 scissor 外繪製）────────────────

    public void renderPopup(float alpha) {
        int a = (int) (255 * alpha);
        int pw = getPopupWidth();
        int ph = getPopupHeight();

        // 背景
        RenderUtil.drawRoundedRect(popupX, popupY, pw, ph, Theme.RADIUS_SM,
                Theme.rgba(Theme.PANEL, a), true, true, true, true);

        float svX = popupX + PAD;
        float svY = popupY + PAD;

        // SV 正方形
        int hueRgb = Color.HSBtoRGB(hue, 1f, 1f);
        RenderUtil.drawRect(svX, svY, svX + SV_SIZE, svY + SV_SIZE, hueRgb);
        drawGradientRect(svX, svY, SV_SIZE, SV_SIZE, 0xFFFFFFFF, 0x00FFFFFF, true);
        drawGradientRect(svX, svY, SV_SIZE, SV_SIZE, 0x00000000, 0xFF000000, false);

        float ix = svX + saturation * SV_SIZE;
        float iy = svY + (1f - brightness) * SV_SIZE;
        RenderUtil.drawCircleOutline(ix, iy, 3f, 2.0f, 0xFF000000);
        RenderUtil.drawCircleOutline(ix, iy, 3f, 1.0f, 0xFFFFFFFF);

        // Hue 條
        float hueY = svY + SV_SIZE + 4;
        drawRainbowRect(svX, hueY, SV_SIZE, HUE_H);
        float hx = svX + hue * SV_SIZE;
        RenderUtil.drawRect(hx - 1, hueY - 1, hx + 1, hueY + HUE_H + 1, 0xFFFFFFFF);
    }

    public boolean mouseClickedPopup(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (!isInsidePopup(mouseX, mouseY)) return false;

        float svX = popupX + PAD;
        float svY = popupY + PAD;

        if (mouseX >= svX && mouseX <= svX + SV_SIZE
                && mouseY >= svY && mouseY <= svY + SV_SIZE) {
            draggingSV = true;
            saturation = clamp((mouseX - svX) / SV_SIZE);
            brightness = clamp(1f - (mouseY - svY) / SV_SIZE);
            updateColor();
            return true;
        }

        float hueY = svY + SV_SIZE + 4;
        if (mouseX >= svX && mouseX <= svX + SV_SIZE
                && mouseY >= hueY && mouseY <= hueY + HUE_H) {
            draggingHue = true;
            hue = clamp((mouseX - svX) / SV_SIZE);
            updateColor();
            return true;
        }
        return true; // 點在小窗空白處也算消費，避免關掉
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isVisible()) return false;
        if (button != 0 && button != 1) return false;

        if (isInsideSwatchArea(mouseX, mouseY)) {
            if (openPicker == this) {
                closePicker();
            } else {
                openPicker = this;
                cachedColor = prop.getValue();
                updateHSB();
            }
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        draggingSV = false;
        draggingHue = false;
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

    private void drawGradientRect(float x, float y, float width, float height,
                                  int startColor, int endColor, boolean horizontal) {
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
