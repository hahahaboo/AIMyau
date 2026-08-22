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
import java.io.IOException;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Unique private float animProgress = 0.0f;
    @Unique private float radialExpand = 0.0f;          // 0 = 收合, 1 = 完全展開
    @Unique private boolean isHoveringRadial = false;
    @Unique private int hoveredButton = -1;            // 1~5

    // 每個按鈕的 hover 放大進度 (0~1)
    @Unique private final float[] buttonHoverAnim = new float[6]; // index 1~5

    // 右下角主圓位置與尺寸
    @Unique private static final float MAIN_CIRCLE_RADIUS = 18f;
    @Unique private static final float EXPANDED_RADIUS = 115f;
    @Unique private static final float SMALL_CIRCLE_RADIUS = 15f;

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
        // 1. Shader 背景
        BackgroundRenderer.draw(this.width, this.height);

        // 2. 標題動畫
        animProgress = AnimationUtil.animate(1.0f, animProgress, 0.12f, 1.0f);
        float titleY = this.height / 2.0f - 80;
        drawTitle(this.width / 2.0f, titleY);

        // 3. 右上角 Theme 斜線按鈕
        drawThemeButton(mouseX, mouseY);

        // 4. 右下角圓形 + 展開扇形
        updateRadialState(mouseX, mouseY);
        drawRadialMenu(mouseX, mouseY);

        // 5. Footer
        drawFooter();

        ci.cancel();
    }

    // ==================== 右上角 Theme 斜線按鈕 ====================
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

        // 白色半透明
        float alpha = hover ? 0.95f : 0.65f;
        GlStateManager.color(1f, 1f, 1f, alpha);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();

        // 兩端小圓點
        drawCircle(x1, y1, hover ? 4.2f : 3.5f, true);
        drawCircle(x2, y2, hover ? 4.2f : 3.5f, true);

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // ==================== 右下角圓形 + 展開邏輯 ====================
    @Unique
    private void updateRadialState(int mouseX, int mouseY) {
        float cx = this.width - 40;
        float cy = this.height - 40;

        float hitRadius = MAIN_CIRCLE_RADIUS + (EXPANDED_RADIUS - MAIN_CIRCLE_RADIUS) * radialExpand + 28f;
        double dist = Math.sqrt((mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy));

        isHoveringRadial = dist <= hitRadius;

        // 展開動畫
        float target = isHoveringRadial ? 1.0f : 0.0f;
        radialExpand = AnimationUtil.animate(target, radialExpand, 0.17f, 1.0f);

        // 計算 hover 哪個按鈕 + 更新放大動畫
        hoveredButton = -1;
        if (radialExpand > 0.55f) {
            for (int i = 1; i <= 5; i++) {
                float[] pos = getButtonPos(i, cx, cy);
                double d = Math.sqrt((mouseX - pos[0]) * (mouseX - pos[0]) + (mouseY - pos[1]) * (mouseY - pos[1]));
                boolean hover = d <= SMALL_CIRCLE_RADIUS + 6;

                // hover 放大動畫
                float hoverTarget = hover ? 1.0f : 0.0f;
                buttonHoverAnim[i] = AnimationUtil.animate(hoverTarget, buttonHoverAnim[i], 0.28f, 1.0f);

                if (hover) hoveredButton = i;
            }
        } else {
            for (int i = 1; i <= 5; i++) {
                buttonHoverAnim[i] = AnimationUtil.animate(0f, buttonHoverAnim[i], 0.25f, 1.0f);
            }
        }
    }

    @Unique
    private float[] getButtonPos(int index, float cx, float cy) {
        // 扇形角度：從右下往左上展開
        float startAngle = -15f;
        float endAngle = -165f;
        float angle = startAngle + (endAngle - startAngle) * ((index - 1) / 4.0f);
        float rad = (float) Math.toRadians(angle);

        float r = EXPANDED_RADIUS * radialExpand;
        float x = cx + (float) Math.cos(rad) * r;
        float y = cy + (float) Math.sin(rad) * r;
        return new float[]{x, y};
    }

    @Unique
    private void drawRadialMenu(int mouseX, int mouseY) {
        float cx = this.width - 40;
        float cy = this.height - 40;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        // ===== 展開的弧線（白色半透明） =====
        if (radialExpand > 0.05f) {
            float alpha = radialExpand * 0.75f;
            GlStateManager.color(1f, 1f, 1f, alpha);

            // 外弧
            drawArc(cx, cy, EXPANDED_RADIUS * radialExpand, -15, -165, 2.6f);
            // 內弧
            drawArc(cx, cy, EXPANDED_RADIUS * 0.52f * radialExpand, -15, -165, 1.9f);
        }

        // ===== 5 個圖示圓形按鈕 =====
        if (radialExpand > 0.25f) {
            for (int i = 1; i <= 5; i++) {
                float[] pos = getButtonPos(i, cx, cy);
                float hover = buttonHoverAnim[i];
                float scale = 1.0f + hover * 0.28f;          // hover 放大 28%
                float r = SMALL_CIRCLE_RADIUS * Math.min(1f, (radialExpand - 0.25f) / 0.5f) * scale;

                GlStateManager.pushMatrix();
                GlStateManager.translate(pos[0], pos[1], 0);
                GlStateManager.scale(scale, scale, 1);

                // 圓形外框（白色）
                float alpha = 0.7f + hover * 0.3f;
                GlStateManager.color(1f, 1f, 1f, alpha);
                drawCircle(0, 0, r, false);

                // 圖示
                drawIcon(i, r * 0.55f, hover);

                GlStateManager.popMatrix();
            }
        }

        // ===== 右下角主圓（永遠存在） =====
        float mainR = MAIN_CIRCLE_RADIUS + (radialExpand * 5f);
        GlStateManager.color(1f, 1f, 1f, 0.9f);
        drawCircle(cx, cy, mainR, false);

        // 展開時主圓內再加一個小實心圓
        if (radialExpand > 0.35f) {
            GlStateManager.color(1f, 1f, 1f, 0.85f * radialExpand);
            drawCircle(cx, cy, 5.5f * radialExpand, true);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // ==================== 圖示繪製（幾何風格） ====================
    @Unique
    private void drawIcon(int id, float size, float hover) {
        GlStateManager.color(1f, 1f, 1f, 0.9f + hover * 0.1f);
        GL11.glLineWidth(1.8f + hover * 0.6f);

        switch (id) {
            case 1: // Singleplayer - 單人房子
                // 屋頂
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2f(-size * 0.7f, 0);
                GL11.glVertex2f(0, -size * 0.75f);
                GL11.glVertex2f(size * 0.7f, 0);
                GL11.glEnd();
                // 牆
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(-size * 0.55f, 0);
                GL11.glVertex2f(size * 0.55f, 0);
                GL11.glVertex2f(size * 0.55f, size * 0.7f);
                GL11.glVertex2f(-size * 0.55f, size * 0.7f);
                GL11.glEnd();
                break;

            case 2: // Multiplayer - 雙人
                // 左人
                drawCircle(-size * 0.4f, -size * 0.25f, size * 0.28f, false);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(-size * 0.4f, size * 0.05f);
                GL11.glVertex2f(-size * 0.4f, size * 0.65f);
                GL11.glEnd();
                // 右人
                drawCircle(size * 0.4f, -size * 0.25f, size * 0.28f, false);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(size * 0.4f, size * 0.05f);
                GL11.glVertex2f(size * 0.4f, size * 0.65f);
                GL11.glEnd();
                break;

            case 3: // Alt Manager - 鑰匙
                // 鑰匙頭
                drawCircle(-size * 0.25f, 0, size * 0.38f, false);
                // 鑰匙柄
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(size * 0.1f, 0);
                GL11.glVertex2f(size * 0.75f, 0);
                // 齒
                GL11.glVertex2f(size * 0.55f, 0);
                GL11.glVertex2f(size * 0.55f, size * 0.35f);
                GL11.glVertex2f(size * 0.7f, 0);
                GL11.glVertex2f(size * 0.7f, size * 0.25f);
                GL11.glEnd();
                break;

            case 4: // Settings - 齒輪
                drawCircle(0, 0, size * 0.35f, false);
                // 簡單齒輪齒
                for (int i = 0; i < 6; i++) {
                    float a = (float) (i * Math.PI / 3);
                    float x1 = (float) Math.cos(a) * size * 0.35f;
                    float y1 = (float) Math.sin(a) * size * 0.35f;
                    float x2 = (float) Math.cos(a) * size * 0.7f;
                    float y2 = (float) Math.sin(a) * size * 0.7f;
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex2f(x1, y1);
                    GL11.glVertex2f(x2, y2);
                    GL11.glEnd();
                }
                break;

            case 5: // Exit - X
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(-size * 0.55f, -size * 0.55f);
                GL11.glVertex2f(size * 0.55f, size * 0.55f);
                GL11.glVertex2f(size * 0.55f, -size * 0.55f);
                GL11.glVertex2f(-size * 0.55f, size * 0.55f);
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
        int steps = 48;
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

    // ==================== 滑鼠點擊處理 ====================
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton != 0) return;

        // 右上角 Theme 按鈕
        float x1 = this.width - 48;
        float y1 = 16;
        float x2 = this.width - 14;
        float y2 = 48;
        if (mouseX >= x1 - 12 && mouseX <= x2 + 12 && mouseY >= y1 - 12 && mouseY <= y2 + 12) {
            this.mc.displayGuiScreen(new GuiBackgroundSelector((GuiScreen) (Object) this));
            ci.cancel();
            return;
        }

        // 展開後的 5 個按鈕
        if (radialExpand > 0.7f && hoveredButton != -1) {
            GuiScreen parent = (GuiScreen) (Object) this;
            switch (hoveredButton) {
                case 1: // Singleplayer
                    this.mc.displayGuiScreen(new GuiSelectWorld(parent));
                    break;
                case 2: // Multiplayer
                    this.mc.displayGuiScreen(new GuiMultiplayer(parent));
                    break;
                case 3: // Alt Manager
                    this.mc.displayGuiScreen(new AltManagerGui((GuiMainMenu) (Object) this));
                    break;
                case 4: // Settings
                    this.mc.displayGuiScreen(new GuiOptions(parent, this.mc.gameSettings));
                    break;
                case 5: // Exit
                    this.mc.shutdown();
                    break;
            }
            ci.cancel();
        }
    }
}
