package myau.mixin;

import myau.Myau;
import myau.management.altmanager.AltManagerGui;
import myau.ui.impl.gui.BackgroundRenderer;
import myau.ui.impl.gui.GuiBackgroundSelector;
import myau.util.AnimationUtil;
import myau.util.font.FontManager;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Unique private float animProgress = 0.0f;
    @Unique private float radialExpand = 0.0f;
    @Unique private boolean isHoveringRadial = false;
    @Unique private int hoveredButton = -1;

    @Unique private final float[] buttonHoverAnim = new float[6];

    // 尺寸參數
    @Unique private static final float MAIN_CIRCLE_RADIUS = 18f;
    @Unique private static final float OUTER_RADIUS = 125f;
    @Unique private static final float INNER_RADIUS = 72f;
    @Unique private static final float BUTTON_RADIUS = 98.5f;        // 兩弧正中間
    @Unique private static final float SMALL_CIRCLE_RADIUS = 15.5f;

    // 真正的 1/4 圓：從主圓正上方 (-90°) 向左下延伸到正左方 (-180°)
    @Unique private static final float START_ANGLE = -90f;
    @Unique private static final float END_ANGLE   = -180f;

    @Inject(method = "initGui", at = @At("TAIL"))
    public void onInitGui(CallbackInfo ci) {
        BackgroundRenderer.init();
        this.buttonList.clear();
        this.animProgress = 0.0f;
        this.radialExpand = 0.0f;
        this.isHoveringRadial = false;
        this.hoveredButton = -1;
        for (int i = 0; i < buttonHoverAnim.length; i++) buttonHoverAnim[i] = 0f;
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    public void onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BackgroundRenderer.draw(this.width, this.height);

        animProgress = AnimationUtil.animate(1.0f, animProgress, 0.12f, 1.0f);
        drawTitle(this.width / 2.0f, this.height / 2.0f - 80);

        drawThemeButton(mouseX, mouseY);
        updateRadialState(mouseX, mouseY);
        drawRadialMenu(mouseX, mouseY);
        drawFooter();

        ci.cancel();
    }

    // ==================== 右上角 Theme 斜線 ====================
    @Unique
    private void drawThemeButton(int mouseX, int mouseY) {
        float x1 = this.width - 48;
        float y1 = 16;
        float x2 = this.width - 14;
        float y2 = 48;

        boolean hover = mouseX >= x1 - 12 && mouseX <= x2 + 12 && mouseY >= y1 - 12 && mouseY <= y2 + 12;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(hover ? 3.2f : 2.4f);

        float alpha = hover ? 0.95f : 0.65f;
        GlStateManager.color(1f, 1f, 1f, alpha);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();

        drawCircle(x1, y1, hover ? 4.2f : 3.5f, true);
        drawCircle(x2, y2, hover ? 4.2f : 3.5f, true);

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // ==================== 狀態更新 ====================
    @Unique
    private void updateRadialState(int mouseX, int mouseY) {
        float cx = this.width - 42;
        float cy = this.height - 42;

        float hitRadius = MAIN_CIRCLE_RADIUS + (OUTER_RADIUS - MAIN_CIRCLE_RADIUS) * radialExpand + 42f;
        double dist = Math.sqrt((mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy));

        isHoveringRadial = dist <= hitRadius;

        float target = isHoveringRadial ? 1.0f : 0.0f;
        radialExpand = AnimationUtil.animate(target, radialExpand, 0.16f, 1.0f);

        hoveredButton = -1;
        if (radialExpand > 0.5f) {
            for (int i = 1; i <= 5; i++) {
                float[] pos = getButtonPos(i, cx, cy);
                double d = Math.sqrt((mouseX - pos[0]) * (mouseX - pos[0]) + (mouseY - pos[1]) * (mouseY - pos[1]));
                boolean hover = d <= SMALL_CIRCLE_RADIUS + 9;

                float hoverTarget = hover ? 1.0f : 0.0f;
                buttonHoverAnim[i] = AnimationUtil.animate(hoverTarget, buttonHoverAnim[i], 0.26f, 1.0f);

                if (hover) hoveredButton = i;
            }
        } else {
            for (int i = 1; i <= 5; i++) {
                buttonHoverAnim[i] = AnimationUtil.animate(0f, buttonHoverAnim[i], 0.22f, 1.0f);
            }
        }
    }

    /**
     * 1/4 圓弧均勻分布
     * 按鈕1 → 主圓正上方 (-90°)
     * 按鈕2~5 → 沿圓弧向左下依序排列，直到 -180°（正左方）
     */
    @Unique
    private float[] getButtonPos(int index, float cx, float cy) {
        float t = (index - 1) / 4.0f;   // 0 ~ 1

        // 從 -90° 線性插值到 -180°（真正的四分之一圓）
        float angle = START_ANGLE + (END_ANGLE - START_ANGLE) * t;
        float rad = (float) Math.toRadians(angle);

        float r = BUTTON_RADIUS * radialExpand;
        float x = cx + (float) Math.cos(rad) * r;
        float y = cy + (float) Math.sin(rad) * r;
        return new float[]{x, y};
    }

    // ==================== 繪製扇形選單 ====================
    @Unique
    private void drawRadialMenu(int mouseX, int mouseY) {
        float cx = this.width - 42;
        float cy = this.height - 42;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        // 兩條弧線（1/4 圓）
        if (radialExpand > 0.04f) {
            float alpha = radialExpand * 0.78f;
            GlStateManager.color(1f, 1f, 1f, alpha);

            drawArc(cx, cy, OUTER_RADIUS * radialExpand, START_ANGLE, END_ANGLE, 2.5f);
            drawArc(cx, cy, INNER_RADIUS * radialExpand, START_ANGLE, END_ANGLE, 2.0f);
        }

        // 5 個按鈕
        if (radialExpand > 0.2f) {
            for (int i = 1; i <= 5; i++) {
                float[] pos = getButtonPos(i, cx, cy);
                float hover = buttonHoverAnim[i];
                float scale = 1.0f + hover * 0.30f;
                float r = SMALL_CIRCLE_RADIUS * Math.min(1f, (radialExpand - 0.2f) / 0.55f) * scale;

                GlStateManager.pushMatrix();
                GlStateManager.translate(pos[0], pos[1], 0);
                GlStateManager.scale(scale, scale, 1);

                float alpha = 0.72f + hover * 0.28f;
                GlStateManager.color(1f, 1f, 1f, alpha);
                drawCircle(0, 0, r, false);

                drawIcon(i, r * 0.55f, hover);

                GlStateManager.popMatrix();
            }
        }

        // 右下角主圓
        float mainR = MAIN_CIRCLE_RADIUS + (radialExpand * 4.5f);
        GlStateManager.color(1f, 1f, 1f, 0.92f);
        drawCircle(cx, cy, mainR, false);

        if (radialExpand > 0.3f) {
            GlStateManager.color(1f, 1f, 1f, 0.88f * radialExpand);
            drawCircle(cx, cy, 5.8f * radialExpand, true);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // ==================== 圖示 ====================
    @Unique
    private void drawIcon(int id, float size, float hover) {
        GlStateManager.color(1f, 1f, 1f, 0.92f + hover * 0.08f);
        GL11.glLineWidth(1.9f + hover * 0.5f);

        switch (id) {
            case 1: // Singleplayer - 房子
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2f(-size * 0.7f, 0);
                GL11.glVertex2f(0, -size * 0.72f);
                GL11.glVertex2f(size * 0.7f, 0);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(-size * 0.52f, 0);
                GL11.glVertex2f(size * 0.52f, 0);
                GL11.glVertex2f(size * 0.52f, size * 0.68f);
                GL11.glVertex2f(-size * 0.52f, size * 0.68f);
                GL11.glEnd();
                break;

            case 2: // Multiplayer - 雙人
                drawCircle(-size * 0.38f, -size * 0.22f, size * 0.26f, false);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(-size * 0.38f, size * 0.05f);
                GL11.glVertex2f(-size * 0.38f, size * 0.62f);
                GL11.glEnd();
                drawCircle(size * 0.38f, -size * 0.22f, size * 0.26f, false);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(size * 0.38f, size * 0.05f);
                GL11.glVertex2f(size * 0.38f, size * 0.62f);
                GL11.glEnd();
                break;

            case 3: // Alt Manager - 鑰匙
                drawCircle(-size * 0.22f, 0, size * 0.36f, false);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(size * 0.12f, 0);
                GL11.glVertex2f(size * 0.72f, 0);
                GL11.glVertex2f(size * 0.52f, 0);
                GL11.glVertex2f(size * 0.52f, size * 0.32f);
                GL11.glVertex2f(size * 0.68f, 0);
                GL11.glVertex2f(size * 0.68f, size * 0.22f);
                GL11.glEnd();
                break;

            case 4: // Settings - 齒輪
                drawCircle(0, 0, size * 0.32f, false);
                for (int i = 0; i < 8; i++) {
                    float a = (float) (i * Math.PI / 4);
                    float x1 = (float) Math.cos(a) * size * 0.32f;
                    float y1 = (float) Math.sin(a) * size * 0.32f;
                    float x2 = (float) Math.cos(a) * size * 0.68f;
                    float y2 = (float) Math.sin(a) * size * 0.68f;
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex2f(x1, y1);
                    GL11.glVertex2f(x2, y2);
                    GL11.glEnd();
                }
                break;

            case 5: // Exit - X
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(-size * 0.52f, -size * 0.52f);
                GL11.glVertex2f(size * 0.52f, size * 0.52f);
                GL11.glVertex2f(size * 0.52f, -size * 0.52f);
                GL11.glVertex2f(-size * 0.52f, size * 0.52f);
                GL11.glEnd();
                break;
        }
    }

    // ==================== 繪圖工具 ====================
    @Unique
    private void drawCircle(float x, float y, float radius, boolean filled) {
        int segments = 48;
        GL11.glBegin(filled ? GL11.GL_TRIANGLE_FAN : GL11.GL_LINE_LOOP);
        if (filled) GL11.glVertex2f(x, y);
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    @Unique
    private void drawArc(float cx, float cy, float radius, float startDeg, float endDeg, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        int steps = 52;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float angle = (float) Math.toRadians(startDeg + (endDeg - startDeg) * t);
            GL11.glVertex2d(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    // ==================== 標題與 Footer ====================
    @Unique
    private void drawTitle(float x, float y) {
        GlStateManager.pushMatrix();
        float s = 1.0f + (1.0f - animProgress) * 0.15f;
        int alpha = (int) (255 * animProgress);
        int color = new Color(255, 255, 255, alpha).getRGB();
        int subColor = new Color(200, 200, 200, Math.max(0, alpha - 60)).getRGB();

        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.translate(-x, -y, 0);

        if (FontManager.nunitoBold80 != null) {
            FontManager.nunitoBold80.drawCenteredString("AIMyau", x, y, color);
            if (FontManager.productSans16 != null) {
                FontManager.productSans16.drawCenteredString(Myau.clientVersion, x, y + 42, subColor);
            }
        } else {
            GlStateManager.scale(3.2, 3.2, 1);
            this.drawCenteredString(this.fontRendererObj, "AIMyau", (int) (x / 3.2), (int) (y / 3.2), color);
        }
        GlStateManager.popMatrix();
    }

    @Unique
    private void drawFooter() {
        String text = "AIMyau 1.8.9";
        int color = new Color(255, 255, 255, 45).getRGB();
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(text, this.width - FontManager.productSans16.getStringWidth(text) - 8, this.height - 14, color);
        } else {
            this.drawString(this.fontRendererObj, text, this.width - this.fontRendererObj.getStringWidth(text) - 8, this.height - 14, color);
        }
    }

    // ==================== 點擊處理 ====================
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton != 0) return;

        // Theme 按鈕
        float x1 = this.width - 48;
        float y1 = 16;
        float x2 = this.width - 14;
        float y2 = 48;
        if (mouseX >= x1 - 12 && mouseX <= x2 + 12 && mouseY >= y1 - 12 && mouseY <= y2 + 12) {
            this.mc.displayGuiScreen(new GuiBackgroundSelector((GuiScreen) (Object) this));
            ci.cancel();
            return;
        }

        // 扇形按鈕
        if (radialExpand > 0.65f && hoveredButton != -1) {
            GuiScreen parent = (GuiScreen) (Object) this;
            switch (hoveredButton) {
                case 1:
                    this.mc.displayGuiScreen(new GuiSelectWorld(parent));
                    break;
                case 2:
                    this.mc.displayGuiScreen(new GuiMultiplayer(parent));
                    break;
                case 3:
                    this.mc.displayGuiScreen(new AltManagerGui((GuiMainMenu) (Object) this));
                    break;
                case 4:
                    this.mc.displayGuiScreen(new GuiOptions(parent, this.mc.gameSettings));
                    break;
                case 5:
                    this.mc.shutdown();
                    break;
            }
            ci.cancel();
        }
    }
}
