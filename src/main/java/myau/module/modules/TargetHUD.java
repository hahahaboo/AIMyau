package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.*;
import myau.util.font.FontManager;
import myau.util.shader.BlurShader;
import myau.util.shader.ShadowShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty style = new ModeProperty("Style", 3, new String[]{
            "ASTOLFO", "EXHIBITION", "MOON", "RISE", "NEVERLOSE", "TENACITY"
    });
    public final ModeProperty animMode = new ModeProperty("Anim Mode", 0, new String[]{"ELASTIC", "SCALE"});
    public final ModeProperty colorMode = new ModeProperty("Color", 0, new String[]{"SYNC", "CUSTOM", "HEALTH", "ASTOLFO"});
    public final ColorProperty customColor = new ColorProperty("CustomColor", new Color(255, 50, 50).getRGB(), () -> colorMode.getValue() == 1);
    public final IntProperty bgAlpha = new IntProperty("Bg-Alpha", 180, 0, 255, () -> style.getValue() != 1);
    public final ModeProperty posX = new ModeProperty("Position-X", 1, new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("Position-Y", 1, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty offX = new IntProperty("Offset-X", 0, -500, 500);
    public final IntProperty offY = new IntProperty("Offset-Y", 40, -500, 500);
    public final BooleanProperty kaOnly = new BooleanProperty("KA Only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("Chat Preview", false);
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final AnimationUtil openingAnimation = new AnimationUtil(AnimationUtil.Easing.EASE_OUT_ELASTIC, 600);
    private final AnimationUtil healthAnimation = new AnimationUtil(AnimationUtil.Easing.EASE_OUT_QUINT, 250);
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase renderTarget = null;

    public TargetHUD() {
        super("TargetHUD", "TargetHUD", Category.RENDER, 0, false, true);
    }

    private boolean shouldUseHudShadow() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.shadow.getValue();
        }
        return false;
    }

    private boolean shouldUseHudBlur() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.background.getValue() && hud.blur.getValue();
        }
        return false;
    }

    private float getHudBlurRadius() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.blurRadius.getValue();
        }
        return 10.0F;
    }

    private int getHudBlurAlpha() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.bgAlpha.getValue();
        }
        return 120;
    }

    private boolean shouldUseHudGlow() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.glow.getValue();
        }
        return false;
    }

    private Color getHudGlowColor() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            long time = System.currentTimeMillis();
            if (hud.glowColorMode.getValue() == 0) {
                return hud.getColor(time, 0);
            } else {
                return new Color(hud.glowCustomColor.getValue());
            }
        }
        return Color.BLACK;
    }

    private int getHudGlowAlpha() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.glowAlpha.getValue();
        }
        return 100;
    }

    private float getHudGlowRadius() {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.isEnabled()) {
            return hud.glowRadius.getValue();
        }
        return 3.0F;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        EntityLivingBase currentTarget = this.resolveTarget();
        boolean isOut = (currentTarget == null);

        if (animMode.getValue() == 0) {
            openingAnimation.setDuration(isOut ? 250 : 650);
            openingAnimation.setEasing(isOut ? AnimationUtil.Easing.EASE_IN_BACK : AnimationUtil.Easing.EASE_OUT_ELASTIC);
        } else {
            openingAnimation.setDuration(isOut ? 200 : 350);
            openingAnimation.setEasing(AnimationUtil.Easing.EASE_OUT_QUINT);
        }

        openingAnimation.run(isOut ? 0 : 1);

        double animValue = openingAnimation.getValue();
        if (animValue <= 0.01 && isOut) {
            this.renderTarget = null;
            return;
        }

        if (currentTarget != null) this.renderTarget = currentTarget;
        if (this.renderTarget == null) return;

        float[] size = getStyleSize();
        float width = size[0];
        float height = size[1];
        float[] pos = getPosition(width, height);

        healthAnimation.run(renderTarget.getHealth());
        float animatedHealth = (float) healthAnimation.getValue();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.enableBlend();

        float finalScale = Math.max(0.001F, (float) (this.scale.getValue() * animValue));
        float centerX = pos[0] + width / 2f;
        float centerY = pos[1] + height / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.scale(finalScale, finalScale, 1.0);
        GlStateManager.translate(-centerX, -centerY, 0);

        checkSetupFBO();

        boolean useBlur = shouldUseHudBlur();
        boolean useShadow = shouldUseHudShadow();
        boolean useGlow = shouldUseHudGlow();

        if (useBlur) {
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);
            RenderUtil.drawRect(pos[0], pos[1], pos[0] + width, pos[1] + height, -1);
            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            int blurColor = ColorUtil.withAlpha(Color.BLACK, getHudBlurAlpha()).getRGB();
            BlurShader.renderBlur(getHudBlurRadius(), pos[0], pos[1], width, height, 1.0f);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }

        if (useGlow) {
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);
            RenderUtil.drawRect(pos[0], pos[1], pos[0] + width, pos[1] + height, -1);
            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            Color glowColor = getHudGlowColor();
            int finalGlowColor = ColorUtil.withAlpha(glowColor, getHudGlowAlpha()).getRGB();

            ShadowShader.drawShadow(
                    pos[0], pos[1], width, height,
                    getHudGlowRadius(), getHudGlowRadius() + 5,
                    finalGlowColor
            );

            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }

        switch (this.style.getValue()) {
            case 0:
                renderAstolfo(pos[0], pos[1], width, height, animatedHealth, useShadow);
                break;
            case 1:
                renderExhibition(pos[0], pos[1], width, height, animatedHealth, useShadow);
                break;
            case 2:
                renderMoon(pos[0], pos[1], width, height, animatedHealth, useShadow);
                break;
            case 3:
                renderRise(pos[0], pos[1], width, height, animatedHealth, useShadow);
                break;
            case 4:
                renderNeverlose(pos[0], pos[1], animatedHealth, useShadow);
                break;
            case 5:
                renderTenacity(pos[0], pos[1], width, height, animatedHealth, useShadow);
                break;
        }

        GlStateManager.popMatrix();
        GL11.glPopAttrib();
        GlStateManager.resetColor();
    }

    private void renderRise(float x, float y, float width, float height, float health, boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shadow) ShadowShader.drawShadow(0, 0, width, height, 8, 5, new Color(0, 0, 0, 100).getRGB());

        RenderUtil.drawRoundedRect(0, 0, width, height, 8, false, new Color(10, 10, 10, bgAlpha.getValue()));

        float healthPct = MathHelper.clamp_float(health / renderTarget.getMaxHealth(), 0, 1);
        float barWidth = width - 48;

        RenderUtil.drawRoundedRect(42, 22f, barWidth, 6, 3, false, new Color(0, 0, 0, 120));

        if (healthPct > 0.01f) {
            float displayW = Math.max(6, barWidth * healthPct);
            drawGradientRoundedRect(42, 22f, displayW, 6, 3, getColor(0), getColor((int) (displayW * 2)));
        }

        drawFace(renderTarget, 4.0f, 3.3f, 33, 33, 6);
        FontManager.productSans18.drawString(String.format("%.1f", health), width - 30, 9, getColor(0).getRGB(), true);
        FontManager.productSans18.drawString(renderTarget.getName(), 42, 9, -1, true);
        GlStateManager.popMatrix();
    }

    private void renderAstolfo(float x, float y, float width, float height, float health, boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shadow) ShadowShader.drawShadow(0, 0, width, height, 6, 6, new Color(0, 0, 0, 100).getRGB());

        RenderUtil.drawRect(0, 0, width, height, new Color(0, 0, 0, bgAlpha.getValue()).getRGB());

        drawEntityOnScreen(25, 45, renderTarget);
        FontManager.regular18.drawString(renderTarget.getName(), 50, 6, -1, true);

        GlStateManager.pushMatrix();
        GlStateManager.scale(1.5, 1.5, 1.5);
        FontManager.regular18.drawString(String.format("%.1f", health) + " ❤", 50 / 1.5f, 22 / 1.5f, getColor(0).getRGB(), true);
        GlStateManager.popMatrix();

        float healthPct = MathHelper.clamp_float(health / renderTarget.getMaxHealth(), 0, 1);
        float barWidth = width - 54;
        RenderUtil.drawRect(48, 42, 48 + barWidth, 49, ColorUtil.darker(getColor(0), 0.3f).getRGB());

        drawHorizontalGradientRect(48, 42, barWidth * healthPct, 7, getColor(0).getRGB(), getColor((int) (barWidth * healthPct * 2)).getRGB());
        GlStateManager.popMatrix();
    }

    private void renderExhibition(float x, float y, float width, float height, float health, boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shadow)
            ShadowShader.drawShadow(-2.5f, -2.5f, width + 5f, height + 5f, 4, 4, new Color(0, 0, 0, 100).getRGB());

        drawExhibitionBorderedRect(-2.5f, -2.5f, width + 2.5f, height + 2.5f, 0.5f, getExhibitionColor(60), getExhibitionColor(10));
        drawExhibitionBorderedRect(-1.5f, -1.5f, width + 1.5f, height + 1.5f, 1.5f, getExhibitionColor(60), getExhibitionColor(40));
        drawExhibitionBorderedRect(0, 0, width, height, 0.5f, getExhibitionColor(22), getExhibitionColor(60));
        drawExhibitionBorderedRect(2, 2, 38, 38, 0.5f, getExhibitionColor(0, 0), getExhibitionColor(10));
        drawExhibitionBorderedRect(2.5f, 2.5f, 37.5f, 37.5f, 0.5f, getExhibitionColor(17), getExhibitionColor(48));
        drawEntityOnScreen(20, 36, renderTarget);

        FontManager.tahomaBold16.drawString(renderTarget.getName(), 46, 4, -1, true);

        float pct = MathHelper.clamp_float(health / renderTarget.getMaxHealth(), 0, 1);
        Color hpColor = blendColors(new float[]{0f, 0.5f, 1f}, new Color[]{Color.RED, Color.YELLOW, Color.GREEN}, pct);

        RenderUtil.drawRect(42, 12, width - 8, 16, getExhibitionColor(0, 0));
        RenderUtil.drawRect(42.5f, 12.5f, 42.5f + (width - 51f) * pct, 15.5f, hpColor.getRGB());

        FontManager.tahomaBold12.drawString("HP: " + (int) health + " | Dist: " + (int) mc.thePlayer.getDistanceToEntity(renderTarget), 46, 19, -1, true);
        GlStateManager.popMatrix();
    }

    private void renderMoon(float x, float y, float width, float height, float health, boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shadow) ShadowShader.drawShadow(0, 0, width, height, 8, 5, new Color(0, 0, 0, 100).getRGB());

        RenderUtil.drawRoundedRect(0, 0, width, height, 8, false, new Color(20, 20, 20, bgAlpha.getValue()));

        float pct = MathHelper.clamp_float(health / renderTarget.getMaxHealth(), 0, 1);
        float barWidth = width - 48;
        RenderUtil.drawRoundedRect(42, 26.5f, barWidth, 8, 4, false, new Color(0, 0, 0, 150));

        if (pct > 0.01f) {
            float displayW = Math.max(8, barWidth * pct);
            drawGradientRoundedRect(42, 26.5f, displayW, 8.5f, 4, getColor(0), getColor((int) (displayW * 2)));
        }

        drawFace(renderTarget, 2.5f, 2.5f, 35, 35, 8);
        FontManager.tenacity12.drawString(String.format("%.1f", health) + "HP", 40, 17, -1, true);
        FontManager.tenacity16.drawString(renderTarget.getName(), 40, 6, -1, true);
        GlStateManager.popMatrix();
    }

    private void renderNeverlose(float x, float y, float health, boolean shadow) {
        float width = Math.max(125.0f, (float) (FontManager.tenacity16.getStringWidth(renderTarget.getName()) + 42));
        float height = 32.5f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shadow) ShadowShader.drawShadow(0, 0, width, height, 4, 4, new Color(0, 0, 0, 150).getRGB());

        RenderUtil.drawRoundedRect(0, 0, width, height, 4, false, new Color(10, 10, 16, bgAlpha.getValue()));

        drawFace(renderTarget, 3f, 3f, 26, 26, 4);

        float circleX = width - 15f;
        drawCircle(circleX, new Color(0, 0, 0, 100).getRGB());
        drawArc(circleX, 360 * MathHelper.clamp_float(health / renderTarget.getMaxHealth(), 0, 1), getColor(0).getRGB());

        FontManager.tenacity16.drawString(renderTarget.getName(), 34, 8, -1, true);
        FontManager.tenacity12.drawString("Dist: " + String.format("%.1f", renderTarget.getDistanceToEntity(mc.thePlayer)) + "m", 34, 20, getColor(0).getRGB(), true);
        GlStateManager.popMatrix();
    }

    private void renderTenacity(float x, float y, float width, float height, float health, boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shadow) ShadowShader.drawShadow(0, 0, width, height, 6, 6, new Color(0, 0, 0, 120).getRGB());

        RenderUtil.drawRoundedRect(0, 0, width, height, 6, false, new Color(0, 0, 0, bgAlpha.getValue()));

        drawFace(renderTarget, 4, 4, 34, 34, 6);
        FontManager.tenacity20.drawString(renderTarget.getName(), 43, 10, -1, true);
        FontManager.tenacity12.drawString("HP: " + String.format("%.1f", health), 43, 20, -1, true);

        float pct = MathHelper.clamp_float(health / renderTarget.getMaxHealth(), 0, 1);
        float barWidth = width - 52;
        RenderUtil.drawRoundedRect(44, 30, barWidth, 6, 3, false, new Color(0, 0, 0, 150));

        if (pct > 0.01f) {
            float displayW = Math.max(6, barWidth * pct);
            drawGradientRoundedRect(44, 30, displayW, 6, 3, getColor(0), getColor((int) (displayW * 2)));
        }
        GlStateManager.popMatrix();
    }

    private void drawGradientRoundedRect(float x, float y, float width, float height, float radius, Color startColor, Color endColor) {
        float r = Math.min(radius, Math.min(width, height) * 0.5f);
        if (width <= 0 || height <= 0) return;

        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GL11.glStencilMask(0xFF);

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);

        GlStateManager.colorMask(false, false, false, false);
        RenderUtil.drawRoundedRect(x, y, width, height, r, -1, true, true, true, true);
        GlStateManager.colorMask(true, true, true, true);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GL11.glStencilMask(0x00);

        drawHorizontalGradientRect(x, y, width, height, startColor.getRGB(), endColor.getRGB());

        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private void drawHorizontalGradientRect(float x, float y, float width, float height, int startColor, int endColor) {
        float startA = (float) (startColor >> 24 & 255) / 255.0F;
        float startR = (float) (startColor >> 16 & 255) / 255.0F;
        float startG = (float) (startColor >> 8 & 255) / 255.0F;
        float startB = (float) (startColor & 255) / 255.0F;
        float endA = (float) (endColor >> 24 & 255) / 255.0F;
        float endR = (float) (endColor >> 16 & 255) / 255.0F;
        float endG = (float) (endColor >> 8 & 255) / 255.0F;
        float endB = (float) (endColor & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);

        worldrenderer.pos(x, y, 0.0D).color(startR, startG, startB, startA).endVertex();
        worldrenderer.pos(x, y + height, 0.0D).color(startR, startG, startB, startA).endVertex();
        worldrenderer.pos(x + width, y + height, 0.0D).color(endR, endG, endB, endA).endVertex();
        worldrenderer.pos(x + width, y, 0.0D).color(endR, endG, endB, endA).endVertex();

        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private Color getColor(int offset) {
        switch (colorMode.getValue()) {
            case 0:
                HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
                if (hud != null) {
                    return hud.getColor(System.currentTimeMillis(), offset);
                }
                return new Color(0, 150, 255);
            case 1:
                return new Color(customColor.getValue());
            case 2:
                return ColorUtil.getHealthBlend(renderTarget.getHealth() / renderTarget.getMaxHealth());
            case 3:
                float h = ((System.currentTimeMillis() + offset) % 3000) / 3000f;
                return Color.getHSBColor(h > 0.5 ? 1f - h : h + 0.5f, 0.5f, 1f);
        }
        return Color.WHITE;
    }

    private float[] getStyleSize() {
        if (renderTarget == null) return new float[]{120, 40};
        String name = renderTarget.getName();
        switch (style.getValue()) {
            case 0:
                return new float[]{Math.max(130, (float) (FontManager.regular18.getStringWidth(name) + 60)), 56};
            case 1:
                return new float[]{Math.max(120, (float) (FontManager.tahomaBold16.getStringWidth(name) + 50)), 40};
            case 2:
                return new float[]{Math.max(110, (float) (FontManager.tenacity16.getStringWidth(name) + 68)), 40.5f};
            case 3:
                return new float[]{Math.max(160, (float) (FontManager.productSans18.getStringWidth(name) + 30)), 40.5f};
            case 4:
                return new float[]{Math.max(125, (float) (FontManager.tenacity16.getStringWidth(name) + 42)), 32.5f};
            case 5:
                return new float[]{Math.max(120, (float) (FontManager.tenacity20.getStringWidth(name) + 50)), 44};
            default:
                return new float[]{120, 40};
        }
    }

    private float[] getPosition(float width, float height) {
        ScaledResolution sr = new ScaledResolution(mc);
        float x = offX.getValue().floatValue();
        float y = offY.getValue().floatValue();
        switch (posX.getValue()) {
            case 1:
                x += sr.getScaledWidth() / 2f - width / 2f;
                break;
            case 2:
                x = sr.getScaledWidth() - width - x;
                break;
        }
        switch (posY.getValue()) {
            case 1:
                y += sr.getScaledHeight() / 2f - height / 2f;
                break;
            case 2:
                y = sr.getScaledHeight() - height - y;
                break;
        }
        return new float[]{x, y};
    }

    private EntityLivingBase resolveTarget() {
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (ka.isEnabled() && ka.getTarget() != null && TeamUtil.isEntityLoaded(ka.getTarget())) return ka.getTarget();
        if (!kaOnly.getValue() && !lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget))
            return lastTarget;
        return (chatPreview.getValue() && mc.currentScreen instanceof GuiChat) ? mc.thePlayer : null;
    }

    private void drawFace(EntityLivingBase entity, float x, float y, float w, float h, float r) {
        if (entity instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getName());
            ResourceLocation skin = (info != null) ? info.getLocationSkin() : new ResourceLocation("textures/entity/steve.png");
            RenderUtil.drawRoundedHead(skin, x, y, w, h, r);
        }
    }

    private void drawEntityOnScreen(int x, int y, EntityLivingBase ent) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 50.0F);
        float size = 16 / Math.max(ent.height / 1.8F, 1);
        GlStateManager.scale(-size, size, size);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        RenderHelper.enableStandardItemLighting();
        RenderManager rm = mc.getRenderManager();
        rm.setRenderShadow(false);
        rm.renderEntityWithPosYaw(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        rm.setRenderShadow(true);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private void drawExhibitionBorderedRect(float x1, float y1, float x2, float y2, float border, int fill, int out) {
        RenderUtil.drawRect(x1, y1, x2, y2, out);
        RenderUtil.drawRect(x1 + border, y1 + border, x2 - border, y2 - border, fill);
    }

    private int getExhibitionColor(int b) {
        return new Color(b, b, b, 255).getRGB();
    }

    private int getExhibitionColor(int b, int a) {
        return new Color(b, b, b, a).getRGB();
    }

    private Color blendColors(float[] fr, Color[] c, float p) {
        if (p >= 1) return c[c.length - 1];
        if (p <= 0) return c[0];
        int i = 0;
        while (fr[i + 1] <= p) i++;
        float f = (p - fr[i]) / (fr[i + 1] - fr[i]);
        return new Color((int) (c[i].getRed() + (c[i + 1].getRed() - c[i].getRed()) * f), (int) (c[i].getGreen() + (c[i + 1].getGreen() - c[i].getGreen()) * f), (int) (c[i].getBlue() + (c[i + 1].getBlue() - c[i].getBlue()) * f));
    }

    private void drawCircle(float x, int color) {
        RenderUtil.enableRenderState();
        RenderUtil.setColor(color);
        GL11.glLineWidth(3f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= 360; i++)
            GL11.glVertex2d(x + Math.sin(Math.toRadians(i)) * 12, (float) 16 + Math.cos(Math.toRadians(i)) * 12);
        GL11.glEnd();
        RenderUtil.disableRenderState();
    }

    private void drawArc(float x, float deg, int color) {
        RenderUtil.enableRenderState();
        RenderUtil.setColor(color);
        GL11.glLineWidth(3f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= deg; i++)
            GL11.glVertex2d(x + Math.sin(Math.toRadians(i)) * 12, (float) 16 - Math.cos(Math.toRadians(i)) * 12);
        GL11.glEnd();
        RenderUtil.disableRenderState();
    }

    private void checkSetupFBO() {
        Framebuffer fbo = mc.getFramebuffer();
        if (fbo != null && fbo.depthBuffer > -1) {
            EXTFramebufferObject.glDeleteRenderbuffersEXT(fbo.depthBuffer);
            int stencil_depth_buffer_id = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(36161, stencil_depth_buffer_id);
            EXTFramebufferObject.glRenderbufferStorageEXT(36161, 34041, mc.displayWidth, mc.displayHeight);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36128, 36161, stencil_depth_buffer_id);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36096, 36161, stencil_depth_buffer_id);
            fbo.depthBuffer = -1;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) event.getPacket();
            if (p.getAction() == Action.ATTACK) {
                Entity e = p.getEntityFromWorld(mc.theWorld);
                if (e instanceof EntityLivingBase && !(e instanceof EntityArmorStand)) {
                    this.lastAttackTimer.reset();
                    this.lastTarget = (EntityLivingBase) e;
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        this.renderTarget = null;
    }
}