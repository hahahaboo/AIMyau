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
    private float expandAnim;
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
        expandAnim = AnimationUtil.animateSmooth(expanded ? 1f : 0f, expandAnim, 14f, dt);

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
        float ty = y + (Theme.MOD_H - 11) / 2f;
        if (FontManager.productSansMedium != null) {
            FontManager.productSansMedium.drawString(module.getName(), x + 18, ty, nameColor);
        } else {
            mc.fontRendererObj.drawStringWithShadow(module.getName(), x + 16, y + 8, nameColor);
        }

        if (!settings.isEmpty()) {
            int sw = 20, sh = 11;
            int sx = x + width - sw - 8;
            int sy = y + (Theme.MOD_H - sh) / 2;   // 若已改用 height 就寫 (height - sh) / 2

            // 軌道
            int track = Theme.rgba(expandAnim > 0.5f ? Theme.ACCENT : Theme.SWITCH_OFF, a);
            RenderUtil.drawRoundedRect(sx, sy, sw, sh, sh / 2f, track, true, true, true, true);

            // 圓點
            int knobX = sx + (int) (expandAnim * (sw - sh));
            RenderUtil.drawRoundedRect(knobX + 1, sy + 1, sh - 2, sh - 2, (sh - 2) / 2f, -1, true, true, true, true);
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

    public boolean isTextFocused() {
        for (SettingElement s : settings) {
            if (s instanceof TextElement && ((TextElement) s).isFocused()) return true;
        }
        return false;
    }

    public void unfocusText() {
        for (SettingElement s : settings) {
            if (s instanceof TextElement) ((TextElement) s).unfocus();
        }
    }

    /** 列超出內容可視區時失焦 */
    public void unfocusTextIfOutside(int contentX, int contentY, int contentW, int contentH) {
        for (SettingElement s : settings) {
            if (!(s instanceof TextElement)) continue;
            TextElement t = (TextElement) s;
            if (!t.isFocused()) continue;
            boolean visible = t.x < contentX + contentW && t.x + t.width > contentX
                    && t.y < contentY + contentH && t.y + t.height > contentY;
            if (!visible) t.unfocus();
        }
    }
}
