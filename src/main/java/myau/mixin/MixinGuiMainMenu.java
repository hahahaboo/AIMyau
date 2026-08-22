package myau.mixin;

import myau.Myau;
import myau.management.altmanager.AltManagerGui;
import myau.ui.impl.gui.BackgroundRenderer;
import myau.ui.impl.gui.GuiBackgroundSelector;
import myau.util.AnimationUtil;
import myau.util.RenderUtil;
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

    // 右下角主圓位置與尺寸（可依解析度微調）
    @Unique private static final float MAIN_CIRCLE_RADIUS = 18f;
    @Unique private static final float EXPANDED_RADIUS = 110f;
    @Unique private static final float SMALL_CIRCLE_RADIUS = 14f;

    @Inject(method = "initGui", at = @At("TAIL"))
    public void onInitGui(CallbackInfo ci) {
        BackgroundRenderer.init();
        this.buttonList.clear();
        this.animProgress = 0.0f;
        this.radialExpand = 0.0f;
        this.isHoveringRadial = false;
        this.hoveredButton = -1;
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
        float x1 = this.width - 45;
        float y1 = 18;
        float x2 = this.width - 12;
        float y2 = 45;

        boolean hover = mouseX >= x1 - 10 && mouseX <= x2 + 10 && mouseY >= y1 - 10 && mouseY <= y2 + 10;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(hover ? 3.0f : 2.2f);

        float alpha = hover ? 1.0f : 0.75f;
        GlStateManager.color(0f, 0f, 0f, alpha);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();

        // 小圓點裝飾（斜線兩端）
        drawCircle(x1, y1, 3.5f, true);
        drawCircle(x2, y2, 3.5f, true);

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // ==================== 右下角圓形 + 展開邏輯 ====================
    @Unique
    private void updateRadialState(int mouseX, int mouseY) {
        float cx = this.width - 38;
        float cy = this.height - 38;

        // 展開後的命中範圍（稍微大一點方便操作）
        float hitRadius = MAIN_CIRCLE_RADIUS + (EXPANDED_RADIUS - MAIN_CIRCLE_RADIUS) * radialExpand + 25f;
        double dist = Math.sqrt((mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy));

        isHoveringRadial = dist <= hitRadius;

        // 動畫
        float target = isHoveringRadial ? 1.0f : 0.0f;
        radialExpand = AnimationUtil.animate(target, radialExpand, 0.18f, 1.0f);

        // 計算目前 hover 哪個按鈕 (1~5)
        hoveredButton = -1;
        if (radialExpand > 0.6f) {
            for (int i = 1; i <= 5; i++) {
                float[] pos = getButtonPos(i, cx, cy);
                double d = Math.sqrt((mouseX - pos[0]) * (mouseX - pos[0]) + (mouseY - pos[1]) * (mouseY - pos[1]));
                if (d <= SMALL_CIRCLE_RADIUS + 4) {
                    hoveredButton = i;
                    break;
                }
            }
        }
    }

    @Unique
    private float[] getButtonPos(int index, float cx, float cy) {
        // 扇形角度：從右下往上展開（約 -20° ~ -160°）
        float startAngle = -20f;
        float endAngle = -160f;
        float angle = startAngle + (endAngle - startAngle) * ((index - 1) / 4.0f);
        float rad = (float) Math.toRadians(angle);

        float r = EXPANDED_RADIUS * radialExpand;
        float x = cx + (float) Math.cos(rad) * r;
        float y = cy + (float) Math.sin(rad) * r;
        return new float[]{x, y};
    }

    @Unique
    private void drawRadialMenu(int mouseX, int mouseY) {
        float cx = this.width - 38;
        float cy = this.height - 38;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);

        // ===== 展開的弧線（ui2.png 的兩條弧） =====
        if (radialExpand > 0.05f) {
            float alpha = radialExpand * 0.9f;
            GlStateManager.color(0f, 0f, 0f, alpha);

            // 外弧
            drawArc(cx, cy, EXPANDED_RADIUS * radialExpand, -20, -160, 2.5f);
            // 內弧
            drawArc(cx, cy, EXPANDED_RADIUS * 0.55f * radialExpand, -20, -160, 1.8f);
        }

        // ===== 5 個數字圓形按鈕 =====
        if (radialExpand > 0.3f) {
            for (int i = 1; i <= 5; i++) {
                float[] pos = getButtonPos(i, cx, cy);
                boolean hover = (hoveredButton == i);

                // 圓形
                float r = SMALL_CIRCLE_RADIUS * Math.min(1f, (radialExpand - 0.3f) / 0.5f);
                GlStateManager.color(0f, 0f, 0f, hover ? 1.0f : 0.85f);
                drawCircle(pos[0], pos[1], r, false); // 空心

                // 數字
                GlStateManager.enableTexture2D();
                String num = String.valueOf(i);
                float scale = 0.9f;
                if (FontManager.productSans20 != null) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(pos[0], pos[1], 0);
                    GlStateManager.scale(scale, scale, 1);
                    FontManager.productSans20.drawCenteredString(num, 0, -FontManager.productSans20.getHeight() / 2f + 1, hover ? 0xFF000000 : 0xFF222222);
                    GlStateManager.popMatrix();
                } else {
                    this.drawCenteredString(this.fontRendererObj, num, (int) pos[0], (int) pos[1] - 4, 0xFF000000);
                }
                GlStateManager.disableTexture2D();
            }
        }

        // ===== 右下角主圓（永遠存在） =====
        float mainR = MAIN_CIRCLE_RADIUS + (radialExpand * 4f); // 展開時稍微變大
        GlStateManager.color(0f, 0f, 0f, 0.95f);
        drawCircle(cx, cy, mainR, false);

        // 展開時主圓內再加一個小實心圓
        if (radialExpand > 0.4f) {
            drawCircle(cx, cy, 6f * radialExpand, true);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
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
        int steps = 40;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float angle = (float) Math.toRadians(startDeg + (endDeg - startDeg) * t);
            GL11.glVertex2d(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    // ==================== 標題與 Footer（保留原本風格） ====================
    @Unique
    private void drawTitle(float x, float y) {
        GlStateManager.pushMatrix();
        float s = 1.0f + (1.0f - animProgress) * 0.15f;
        int alpha = (int) (255 * animProgress);
        int color = new Color(255, 255, 255, alpha).getRGB();
        int subColor = new Color(180, 180, 180, Math.max(0, alpha - 80)).getRGB();

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
        int color = new Color(255, 255, 255, 40).getRGB();
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(text, this.width - FontManager.productSans16.getStringWidth(text) - 6, this.height - 12, color);
        } else {
            this.drawString(this.fontRendererObj, text, this.width - this.fontRendererObj.getStringWidth(text) - 6, this.height - 12, color);
        }
    }

    // ==================== 滑鼠點擊處理 ====================
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton != 0) return;

        // 右上角 Theme 按鈕
        float x1 = this.width - 45;
        float y1 = 18;
        float x2 = this.width - 12;
        float y2 = 45;
        if (mouseX >= x1 - 10 && mouseX <= x2 + 10 && mouseY >= y1 - 10 && mouseY <= y2 + 10) {
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
