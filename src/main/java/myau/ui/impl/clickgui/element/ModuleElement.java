package myau.ui.impl.clickgui.element;

import myau.Myau;
import myau.module.Module;
import myau.property.Property;
import myau.property.properties.*;
import myau.ui.impl.clickgui.Theme;
import myau.ui.impl.clickgui.element.setting.*;
import myau.util.AnimationUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;

import java.util.ArrayList;
import java.util.List;

public class ModuleElement extends Element {
    private final Module module;
    private final List<SettingElement> settings = new ArrayList<>();
    private boolean expanded;
    private float hoverAnim;
    /** 目前設定區高度（像素），固定速度開合 */
    private float drawnH;
    private static final float EXPAND_SPEED = 800f;

    public ModuleElement(Module module, int x, int y, int width) {
        super(x, y, width, Theme.MOD_H);
        this.module = module;
        buildSettings();
    }

    private void buildSettings() {
        settings.clear();
        settings.add(new KeybindElement(module, 0, 0, width));

        if (Myau.propertyManager == null) return;
        List<Property<?>> props = Myau.propertyManager.properties.get(module.getClass());
        if (props == null) return;

        for (Property<?> p : props) {
            if (p instanceof BooleanProperty) {
                settings.add(new BooleanElement((BooleanProperty) p, 0, 0, width));
            } else if (p instanceof IntProperty || p instanceof FloatProperty || p instanceof PercentProperty) {
                settings.add(new SliderElement(p, 0, 0, width));
            } else if (p instanceof ModeProperty) {
                settings.add(new ModeElement((ModeProperty) p, 0, 0, width));
            } else if (p instanceof TextProperty) {
                settings.add(new TextElement((TextProperty) p, 0, 0, width));
            } else if (p instanceof ColorProperty) {
                settings.add(new ColorElement((ColorProperty) p, 0, 0, width));
            }
        }
    }

    private float getSettingsTotalHeight() {
        float h = 0;
        for (SettingElement s : settings) {
            if (s.isVisible()) h += s.getHeight();
        }
        return h;
    }

    public float getCurrentHeight() {
        return Theme.MOD_H + drawnH;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        int a = (int) (255 * alpha);
        boolean hover = isHovered(mouseX, mouseY) && mouseY < y + Theme.MOD_H;

        float dt = Element.deltaTime > 0f ? Element.deltaTime : 0.016f;
        hoverAnim = AnimationUtil.animateSmooth(hover ? 1f : 0f, hoverAnim, 12f, dt);

        float settingsH = getSettingsTotalHeight();
        float targetH = expanded ? settingsH : 0f;
        // 固定像素速度：設定越多開越久，手感速度一致
        drawnH = AnimationUtil.animate(targetH, drawnH, EXPAND_SPEED, dt);
        if (drawnH > settingsH) drawnH = settingsH;
        if (drawnH < 0f) drawnH = 0f;

        // 標題列
        int bg = Theme.rgba(hoverAnim > 0.01f ? Theme.MODULE_HOVER : Theme.MODULE, a);
        RenderUtil.drawRoundedRect(x, y, width, Theme.MOD_H, Theme.RADIUS_SM, bg, true, true, true, true);

        if (module.isEnabled()) {
            RenderUtil.drawRoundedRect(x + 8, y + Theme.MOD_H / 2f - 2.5f, 5, 5, 2.5f,
                    Theme.rgba(Theme.ACCENT, a), true, true, true, true);
        }

        int nameColor = module.isEnabled() ? Theme.rgba(Theme.ACCENT, a) : Theme.rgba(Theme.TEXT, a);
        float ty = y + (Theme.MOD_H - 8) / 2f;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(module.getName(), x + 18, ty, nameColor);
        } else {
            mc.fontRendererObj.drawStringWithShadow(module.getName(), x + 16, y + 8, nameColor);
        }

        if (!settings.isEmpty()) {
            String arrow = expanded ? "v" : "^";
            if (FontManager.productSans16 != null) {
                float aw = (float) FontManager.productSans16.getStringWidth(arrow);
                FontManager.productSans16.drawString(arrow, x + width - aw - 10, ty, Theme.rgba(Theme.TEXT_DIM, a));
            }
        }

        // 設定區
        if (drawnH > 0.5f) {
            float sy = y + Theme.MOD_H;
            float visibleBottom = sy + drawnH;

            RenderUtil.drawRoundedRect(x, sy, width, drawnH, Theme.RADIUS_SM,
                    Theme.rgba(Theme.SETTING_BG, (int) (a * 0.9f)), false, false, true, true);

            float cy = sy;
            for (SettingElement s : settings) {
                if (!s.isVisible()) continue;

                float sh = s.getHeight();
                float itemTop = cy;
                float itemBottom = cy + sh;

                if (itemBottom <= sy || itemTop >= visibleBottom) {
                    cy += sh;
                    continue;
                }
                // 整塊在可視區內才畫
                if (itemBottom > visibleBottom + 0.5f) {
                    cy += sh;
                    continue;
                }

                s.x = x + 6;
                s.y = (int) cy;
                s.width = width - 12;
                s.render(mouseX, mouseY, partialTicks, alpha);
                cy += sh;
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + Theme.MOD_H) {
            if (button == 0) {
                module.toggle();
                return true;
            } else if (button == 1 && !settings.isEmpty()) {
                expanded = !expanded;
                return true;
            }
        }

        if (expanded && drawnH > 8f) {
            float sy = y + Theme.MOD_H;
            float visibleBottom = sy + drawnH;

            float cy = sy;
            for (SettingElement s : settings) {
                if (!s.isVisible()) continue;
                float sh = s.getHeight();
                float itemBottom = cy + sh;

                if (itemBottom <= sy || cy >= visibleBottom) {
                    cy += sh;
                    continue;
                }
                if (itemBottom > visibleBottom + 0.5f) {
                    cy += sh;
                    continue;
                }

                s.x = x + 6;
                s.y = (int) cy;
                s.width = width - 12;
                if (s.mouseClicked(mouseX, mouseY, button)) return true;
                cy += sh;
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (SettingElement s : settings) {
            s.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        for (SettingElement s : settings) {
            s.keyTyped(typedChar, keyCode);
        }
    }

    public boolean isBinding() {
        for (SettingElement s : settings) {
            if (s instanceof KeybindElement && ((KeybindElement) s).isBinding()) return true;
        }
        return false;
    }
}
