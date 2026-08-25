package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.AnimationUtil;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.impl.FontRenderer;
import myau.util.shader.BlurShader;
import myau.util.shader.ShadowShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty fontMode = new ModeProperty("font", 1, new String[]{"Minecraft", "Product_Sans", "Regular", "Tenacity", "Vision", "NBP_INFORMA", "Tahoma_Bold", "Nunito_Bold", "HarmonyOS_Sans"});
    public final ModeProperty arraylistMode = new ModeProperty("position", 1, new String[]{"Left", "Right"});
    public final IntProperty offsetX = new IntProperty("offset-x", 6, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 6, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty textHeight = new FloatProperty("line-height", 11F, 8F, 20F);
    public final FloatProperty bgPadding = new FloatProperty("bg-padding", 2.0F, 0.0F, 10.0F);
    public final ModeProperty animation = new ModeProperty("animation", 0, new String[]{"Slide", "Smooth"});
    public final FloatProperty animSpeed = new FloatProperty("anim-speed", 2.0F, 0.1F, 10.0F);
    public final ModeProperty colorMode = new ModeProperty("text-color", 1, new String[]{"Static", "Fade", "Breathe", "Rainbow", "Astolfo"});
    public final ColorProperty custom1 = new ColorProperty("color-1", new Color(0, 111, 255).getRGB(), () -> colorMode.getValue() <= 2);
    public final ColorProperty custom2 = new ColorProperty("color-2", new Color(0, 255, 255).getRGB(), () -> colorMode.getValue() == 1 || colorMode.getValue() == 2);
    public final ColorProperty custom3 = new ColorProperty("color-3", new Color(255, 255, 255).getRGB(), () -> colorMode.getValue() == 2);
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.1F, 5.0F);
    public final FloatProperty colorDistance = new FloatProperty("color-dist", 50F, 10F, 100F);
    public final BooleanProperty shadow = new BooleanProperty("text-shadow", true);
    public final FloatProperty textYOffset = new FloatProperty("text-y-offset", 0.0F, -5.0F, 5.0F);

    public final BooleanProperty background = new BooleanProperty("background", true);
    public final BooleanProperty blur = new BooleanProperty("blur", false, background::getValue);
    public final FloatProperty blurRadius = new FloatProperty("blur-radius", 10.0F, 1.0F, 30.0F, () -> background.getValue() && blur.getValue());
    public final ColorProperty bgColor = new ColorProperty("bg-color", new Color(0, 0, 0).getRGB(), background::getValue);
    public final IntProperty bgAlpha = new IntProperty("bg-alpha", 120, 0, 255, background::getValue);

    public final BooleanProperty sidebar = new BooleanProperty("sidebar", true);
    public final BooleanProperty sidebarSync = new BooleanProperty("bar-sync", true, sidebar::getValue);
    public final ColorProperty sidebarColor = new ColorProperty("bar-color", new Color(0, 111, 255).getRGB(), () -> sidebar.getValue() && !sidebarSync.getValue());
    public final FloatProperty sidebarWidth = new FloatProperty("bar-width", 2.0F, 0.5F, 5.0F, sidebar::getValue);
    public final FloatProperty sidebarHeight = new FloatProperty("bar-height", 1.0F, 0.1F, 5.0F, sidebar::getValue);

    public final BooleanProperty glow = new BooleanProperty("glow", true);
    public final ModeProperty glowColorMode = new ModeProperty("glow-mode", 1, new String[]{"Sync", "Custom"}, glow::getValue);
    public final ColorProperty glowCustomColor = new ColorProperty("glow-color", new Color(0, 0, 0).getRGB(), () -> glow.getValue() && glowColorMode.getValue() == 1);
    public final IntProperty glowAlpha = new IntProperty("glow-alpha", 100, 0, 255, glow::getValue);
    public final FloatProperty glowRadius = new FloatProperty("glow-radius", 3f, 1f, 30f, glow::getValue);

    public final BooleanProperty tags = new BooleanProperty("tags", true);
    public final ModeProperty tagsStyle = new ModeProperty("tags-style", 5, new String[]{"[]", "()", "<>", "-", "|", "Space"}, tags::getValue);
    public final ModeProperty suffixMode = new ModeProperty("suffix-color", 0, new String[]{"Gray", "White", "Color"}, tags::getValue);
    public final BooleanProperty lowercase = new BooleanProperty("lowercase", false);

    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);

    private final Map<Module, Float> animationMap = new HashMap<>();
    private List<Module> sortedModules = new ArrayList<>();

    public HUD() {
        super("HUD", "Highly customizable Arraylist", Category.RENDER, 0, true, true);
    }

    private FontRenderer getCustomFont() {
        switch (fontMode.getValue()) {
            case 1:
                if (FontManager.productSans20 != null) return FontManager.productSans20;
                break;
            case 2:
                if (FontManager.regular22 != null) return FontManager.regular22;
                break;
            case 3:
                if (FontManager.tenacity20 != null) return FontManager.tenacity20;
                break;
            case 4:
                if (FontManager.vision20 != null) return FontManager.vision20;
                break;
            case 5:
                if (FontManager.nbpInforma20 != null) return FontManager.nbpInforma20;
                break;
            case 6:
                if (FontManager.tahomaBold20 != null) return FontManager.tahomaBold20;
                break;
            case 7:
                if (FontManager.nunitoBold20 != null) return FontManager.nunitoBold20;
                break;
            case 8:
                if (FontManager.harmonyOS_Sans20 != null) return FontManager.harmonyOS_Sans20;
                break;
        }
        return null;
    }

    private float getFontHeight() {
        FontRenderer fr = getCustomFont();
        return fr != null ? (float) fr.getHeight() : mc.fontRendererObj.FONT_HEIGHT;
    }

    private float getStringWidth(String text) {
        FontRenderer fr = getCustomFont();
        return fr != null ? (float) fr.getStringWidth(text) : mc.fontRendererObj.getStringWidth(text);
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        FontRenderer fr = getCustomFont();
        if (fr != null) {
            if (shadow) fr.drawStringWithShadow(text, x, y, color);
            else fr.drawString(text, x, y, color);
        } else {
            if (shadow) mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
            else mc.fontRendererObj.drawString(text, x, y, color, false);
        }
    }

    private String getModuleDisplay(Module module) {
        String name = module.getName();
        if (lowercase.getValue()) name = name.toLowerCase();

        if (!tags.getValue()) return name;

        String suffix = (module.getSuffix() != null && module.getSuffix().length > 0) ? module.getSuffix()[0] : null;
        if (suffix == null || suffix.isEmpty()) return name;

        if (lowercase.getValue()) suffix = suffix.toLowerCase();

        String tagStr = "";

        String colorCode = "";
        int suffixColorValue = suffixMode.getValue();

        switch (suffixColorValue) {
            case 0:
                colorCode = " §7";
                break;
            case 1:
                colorCode = " §f";
                break;
            case 2:
                colorCode = " ";
                break;
        }

        switch (tagsStyle.getValue()) {
            case 0:
                tagStr = "[" + suffix + "]";
                break;
            case 1:
                tagStr = "(" + suffix + ")";
                break;
            case 2:
                tagStr = "<" + suffix + ">";
                break;
            case 3:
                tagStr = "- " + suffix;
                break;
            case 4:
                tagStr = "| " + suffix;
                break;
            case 5:
                tagStr = suffix;
                break;
        }
        return name + colorCode + tagStr;
    }

    public Color getColor(long time, long offset) {
        int color = -1;
        switch (colorMode.getValue()) {
            case 0:
                color = custom1.getValue();
                break;
            case 1:
                color = ColorUtil.interpolate(
                        0.5f + 0.5f * (float) Math.sin((time - offset * 10) / (1000.0 / colorSpeed.getValue())),
                        new Color(custom1.getValue()),
                        new Color(custom2.getValue())
                ).getRGB();
                break;
            case 2:
                float progress = (time % (long) (2000 / colorSpeed.getValue()) + offset * 10) / (2000f / colorSpeed.getValue());
                if (progress > 1) progress = progress % 1;

                Color c1 = new Color(custom1.getValue());
                Color c2 = new Color(custom2.getValue());
                Color c3 = new Color(custom3.getValue());

                if (progress < 0.5) {
                    color = ColorUtil.interpolate(progress * 2, c1, c2).getRGB();
                } else {
                    color = ColorUtil.interpolate((progress - 0.5f) * 2, c2, c3).getRGB();
                }
                break;
            case 3:
                color = ColorUtil.fromHSB((time % (int) (1000 / colorSpeed.getValue()) + offset) / (1000f / colorSpeed.getValue()), 0.8f, 1f).getRGB();
                break;
            case 4:
                color = ColorUtil.astolfoColors((int) offset, 1000);
                break;
        }
        return new Color(color);
    }

    public Color getColor(long time) {
        return getColor(time, 0);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        sortedModules = Myau.moduleManager.modules.values().stream()
                .filter(m -> !m.isHidden() && (m.isEnabled() || animationMap.getOrDefault(m, 0f) > 0.01f))
                .sorted((m1, m2) -> Float.compare(getStringWidth(getModuleDisplay(m2)), getStringWidth(getModuleDisplay(m1))))
                .collect(Collectors.toList());
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(2.0F, (float) (mc.currentScreen.height - 14), (float) (mc.currentScreen.width - 2), (float) (mc.currentScreen.height - 2), 1.5F, 0, new Color(0, 111, 255).getRGB());
                RenderUtil.disableRenderState();
            }
        }

        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) return;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale.getValue(), scale.getValue(), 1.0F);

        boolean right = arraylistMode.getValue() == 1;
        float screenWidth = (float) new ScaledResolution(mc).getScaledWidth() / scale.getValue();
        float currentY = offsetY.getValue();
        float startX = right ? screenWidth - offsetX.getValue() : offsetX.getValue();

        float deltaTime = 1.0f / Math.max(Minecraft.getDebugFPS(), 5);

        checkSetupFBO();

        if (background.getValue() && blur.getValue()) {
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);

            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);

            float blurMaskY = currentY;

            for (Module module : sortedModules) {
                String displayText = getModuleDisplay(module);
                float width = getStringWidth(displayText);

                float targetSlide = module.isEnabled() ? width : 0f;
                float currentSlide = animationMap.getOrDefault(module, 0f);

                if (animation.getValue() == 0) {
                    float speed = animSpeed.getValue() * 100f * deltaTime;
                    if (Math.abs(currentSlide - targetSlide) > speed) {
                        currentSlide += (currentSlide < targetSlide) ? speed : -speed;
                    } else currentSlide = targetSlide;
                } else {
                    currentSlide = AnimationUtil.animateSmooth(targetSlide, currentSlide, animSpeed.getValue() * 10f, deltaTime);
                }
                if (currentSlide < 0) currentSlide = 0;
                if (currentSlide > width) currentSlide = width;
                animationMap.put(module, currentSlide);

                if (currentSlide <= 0.1f) continue;

                float rectH = textHeight.getValue();
                float xPos = right ? (startX - currentSlide) : (startX - width + currentSlide);

                float padding = bgPadding.getValue();
                float bgX1 = xPos - padding;
                float bgX2 = xPos + width + padding;

                float barW = sidebarWidth.getValue();
                if (sidebar.getValue()) {
                    if (right) bgX2 += barW;
                    else bgX1 -= barW;
                }

                RenderUtil.drawRect(bgX1, blurMaskY, bgX2, blurMaskY + rectH, -1);
                blurMaskY += rectH;
            }

            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            BlurShader.renderBlur(blurRadius.getValue(), 0, 0, screenWidth, new ScaledResolution(mc).getScaledHeight() / scale.getValue(), 1.0f);

            GL11.glDisable(GL11.GL_STENCIL_TEST);
        } else {
            for (Module module : sortedModules) {
                String displayText = getModuleDisplay(module);
                float width = getStringWidth(displayText);
                float targetSlide = module.isEnabled() ? width : 0f;
                float currentSlide = animationMap.getOrDefault(module, 0f);
                if (animation.getValue() == 0) {
                    float speed = animSpeed.getValue() * 100f * deltaTime;
                    if (Math.abs(currentSlide - targetSlide) > speed) {
                        currentSlide += (currentSlide < targetSlide) ? speed : -speed;
                    } else currentSlide = targetSlide;
                } else {
                    currentSlide = AnimationUtil.animateSmooth(targetSlide, currentSlide, animSpeed.getValue() * 10f, deltaTime);
                }
                if (currentSlide < 0) currentSlide = 0;
                if (currentSlide > width) currentSlide = width;
                animationMap.put(module, currentSlide);
            }
        }

        if (glow.getValue() && background.getValue()) {
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);

            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);

            float maskY = currentY;

            for (Module module : sortedModules) {
                float currentSlide = animationMap.getOrDefault(module, 0f);
                if (currentSlide <= 0.1f) continue;

                String displayText = getModuleDisplay(module);
                float width = getStringWidth(displayText);
                float rectH = textHeight.getValue();
                float xPos = right ? (startX - currentSlide) : (startX - width + currentSlide);

                float padding = bgPadding.getValue();
                float bgX1 = xPos - padding;
                float bgX2 = xPos + width + padding;

                float barW = sidebarWidth.getValue();
                if (sidebar.getValue()) {
                    if (right) bgX2 += barW;
                    else bgX1 -= barW;
                }

                RenderUtil.drawRect(bgX1, maskY, bgX2, maskY + rectH, -1);
                maskY += rectH;
            }

            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            long time = System.currentTimeMillis();
            int colorAlpha = glowAlpha.getValue();

            float glowMaskY = currentY;

            for (Module module : sortedModules) {
                float currentSlide = animationMap.getOrDefault(module, 0f);
                if (currentSlide <= 0.1f) continue;

                String displayText = getModuleDisplay(module);
                float width = getStringWidth(displayText);
                float rectH = textHeight.getValue();
                float xPos = right ? (startX - currentSlide) : (startX - width + currentSlide);

                float padding = bgPadding.getValue();
                float bgX1 = xPos - padding;
                float bgX2 = xPos + width + padding;

                float barW = sidebarWidth.getValue();
                if (sidebar.getValue()) {
                    if (right) bgX2 += barW;
                    else bgX1 -= barW;
                }

                if (colorAlpha > 0) {
                    Color baseGlowColor;
                    if (glowColorMode.getValue() == 0) {
                        int offset = (int) (glowMaskY * 0.5f);
                        baseGlowColor = this.getColor(time, offset);
                    } else {
                        baseGlowColor = new Color(glowCustomColor.getValue());
                    }

                    int glowColor = ColorUtil.withAlpha(baseGlowColor, colorAlpha).getRGB();

                    ShadowShader.drawShadow(
                            bgX1, glowMaskY, bgX2 - bgX1, rectH,
                            glowRadius.getValue(), glowRadius.getValue() + 5,
                            glowColor
                    );
                }

                glowMaskY += rectH;
            }

            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }

        int count = 0;
        long time = System.currentTimeMillis();

        for (Module module : sortedModules) {
            float currentSlide = animationMap.getOrDefault(module, 0f);
            if (currentSlide <= 0.1f) {
                count++;
                continue;
            }

            String displayText = getModuleDisplay(module);
            float width = getStringWidth(displayText);
            float rectH = textHeight.getValue();
            float xPos = right ? (startX - currentSlide) : (startX - width + currentSlide);
            float paddingY = (rectH - getFontHeight()) / 2f;

            int offset = (int) (count * colorDistance.getValue());
            int color = this.getColor(time, offset).getRGB();

            float padding = bgPadding.getValue();
            float bgX1 = xPos - padding;
            float bgX2 = xPos + width + padding;

            float barW = sidebarWidth.getValue();

            if (sidebar.getValue()) {
                if (right) bgX2 += barW;
                else bgX1 -= barW;
            }

            if (background.getValue()) {
                int finalBgColor = ColorUtil.withAlpha(new Color(bgColor.getValue()), bgAlpha.getValue()).getRGB();
                RenderUtil.drawRect(bgX1, currentY, bgX2, currentY + rectH, finalBgColor);
            }

            if (sidebar.getValue()) {
                int sideColor = sidebarSync.getValue() ? color : sidebarColor.getValue();
                float adjustedSidebarHeight = rectH * sidebarHeight.getValue();
                float sidebarOffsetY = (rectH - adjustedSidebarHeight) / 2;
                if (right) {
                    RenderUtil.drawRect(bgX2 - barW, currentY + sidebarOffsetY, bgX2, currentY + sidebarOffsetY + adjustedSidebarHeight, sideColor);
                } else {
                    RenderUtil.drawRect(bgX1, currentY + sidebarOffsetY, bgX1 + barW, currentY + sidebarOffsetY + adjustedSidebarHeight, sideColor);
                }
            }

            float textY = currentY + paddingY + textYOffset.getValue();
            if (getCustomFont() == null) textY += 1.0f;

            this.drawString(displayText, xPos, textY, color, shadow.getValue());

            currentY += rectH;
            count++;
        }

        if (this.blinkTimer.getValue()) {
            if (Myau.blinkManager.getBlinkingModule() != BlinkModules.NONE && Myau.blinkManager.getBlinkingModule() != BlinkModules.AUTO_BLOCK) {
                long packetSize = Myau.blinkManager.countMovement();
                if (packetSize > 0L) {
                    String blinkText = String.valueOf(packetSize);
                    float bx = (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / scale.getValue() - getStringWidth(blinkText) / 2.0F;
                    float by = (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / scale.getValue() + 15;
                    this.drawString(blinkText, bx, by, new Color(0, 111, 255).getRGB(), true);
                }
            }
        }

        GlStateManager.popMatrix();
    }

    private void checkSetupFBO() {
        Framebuffer fbo = mc.getFramebuffer();
        if (fbo != null && fbo.depthBuffer > -1) {
            EXTFramebufferObject.glDeleteRenderbuffersEXT(fbo.depthBuffer);
            int stencil_depth_buffer_id = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, stencil_depth_buffer_id);
            EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT, mc.displayWidth, mc.displayHeight);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, stencil_depth_buffer_id);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, stencil_depth_buffer_id);
            fbo.depthBuffer = -1;
        }
    }
}