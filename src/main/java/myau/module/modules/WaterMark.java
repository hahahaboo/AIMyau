package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.TextProperty;
import myau.util.font.FontManager;
import myau.util.font.impl.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Exhibition", "Modern"});

    public final TextProperty modernText = new TextProperty("Text", "AIMyau", () -> mode.getValue() == 1);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true, () -> mode.getValue() == 1);

    public WaterMark() {
        super("WaterMark", "Just watermark,hmm", Category.RENDER, 0, false, true);
    }

    private FontRenderer getCustomFont() {
        HUD hud = (HUD) Myau.moduleManager.getModule("HUD");
        if (hud != null) {
            switch (hud.fontMode.getValue()) {
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
            }
        }
        return null;
    }

    private float getStringWidth(String text) {
        FontRenderer fr = getCustomFont();
        if (fr != null) {
            return (float) fr.getStringWidth(text);
        }
        return mc.fontRendererObj.getStringWidth(text);
    }

    private void drawStringWithShadow(String text, float x, float y, int color) {
        FontRenderer fr = getCustomFont();
        if (fr != null) {
            fr.drawStringWithShadow(text, x, y, color);
        } else {
            mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;

        if (mode.getValue() == 0) {
            renderExhibition();
        } else {
            renderModern();
        }
    }

    private void renderModern() {
        FontRenderer fr = FontManager.nunitoBold48;
        boolean customFont = fr != null;

        HUD hud = (HUD) Myau.moduleManager.getModule("HUD");

        String text = modernText.getValue();
        float x = 4.0f;
        float y = 4.0f;
        long time = System.currentTimeMillis();

        GlStateManager.pushMatrix();

        char[] characters = text.toCharArray();
        float currentX = x;

        for (int i = 0; i < characters.length; i++) {
            String charStr = String.valueOf(characters[i]);

            int color = 0xFFFFFFFF;
            if (hud != null) {
                long offset = (long) (i * hud.colorDistance.getValue());
                color = hud.getColor(time, offset).getRGB();
            }

            if (customFont) {
                if (shadow.getValue()) {
                    fr.drawStringWithShadow(charStr, currentX, y, color);
                } else {
                    fr.drawString(charStr, currentX, y, color);
                }
                currentX += (float) fr.getStringWidth(charStr);
            } else {
                mc.fontRendererObj.drawString(charStr, currentX, y, color, shadow.getValue());
                currentX += mc.fontRendererObj.getStringWidth(charStr);
            }
        }

        GlStateManager.popMatrix();
    }

    private void renderExhibition() {
        int fps = Minecraft.getDebugFPS();
        int ping = 0;

        if (mc.thePlayer != null && mc.theWorld != null) {
            if (mc.thePlayer.sendQueue != null && mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                ping = mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
            }
        }

        String exhibitionText = "E";
        String restText = "xhibition ";
        String fpsValue = fps + "FPS";
        String pingValue = ping + "ms";

        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);

        float x = 2.0f;
        float y = 2.0f;

        if (getCustomFont() != null) {
            y += 1.0f;
        }

        GlStateManager.pushMatrix();

        long time = System.currentTimeMillis();
        int rainbowColor = hud != null ? hud.getColor(time).getRGB() : 0xFFFFFFFF;

        drawStringWithShadow(exhibitionText, x, y, rainbowColor);
        float currentX = x + getStringWidth(exhibitionText);

        int whiteColor = 0xFFFFFFFF;
        drawStringWithShadow(restText, currentX, y, whiteColor);
        currentX += getStringWidth(restText);

        int grayColor = 0xFFAAAAAA;
        drawStringWithShadow("[", currentX, y, grayColor);
        currentX += getStringWidth("[");

        drawStringWithShadow(fpsValue, currentX, y, whiteColor);
        currentX += getStringWidth(fpsValue);

        drawStringWithShadow("]", currentX, y, grayColor);
        currentX += getStringWidth("]");

        String space = " ";
        drawStringWithShadow(space, currentX, y, whiteColor);
        currentX += getStringWidth(space);

        drawStringWithShadow("[", currentX, y, grayColor);
        currentX += getStringWidth("[");

        drawStringWithShadow(pingValue, currentX, y, whiteColor);
        currentX += getStringWidth(pingValue);

        drawStringWithShadow("]", currentX, y, grayColor);

        GlStateManager.popMatrix();
    }
}
