package myau.ui.impl.clickgui.element;

import myau.module.Category;
import myau.ui.impl.clickgui.Theme;
import myau.util.RenderUtil;
import myau.util.font.FontManager;

public class CategoryElement extends Element {
    private final Category category;
    private boolean selected;

    public CategoryElement(Category category, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        int a = (int) (255 * alpha);
        boolean hover = isHovered(mouseX, mouseY);

        if (selected) {
            RenderUtil.drawRoundedRect(x + 4, y + 2, width - 8, height - 4, Theme.RADIUS_SM,
                    Theme.rgba(Theme.ACCENT, (int)(a * 0.25f)), true, true, true, true);
            // 左側小條
            RenderUtil.drawRoundedRect(x + 4, y + 6, 3, height - 12, 1.5f,
                    Theme.rgba(Theme.ACCENT, a), true, true, true, true);
        } else if (hover) {
            RenderUtil.drawRoundedRect(x + 4, y + 2, width - 8, height - 4, Theme.RADIUS_SM,
                    Theme.rgba(Theme.MODULE_HOVER, a), true, true, true, true);
        }

        int color = selected ? Theme.rgba(Theme.ACCENT, a) : Theme.rgba(hover ? Theme.TEXT : Theme.TEXT_DIM, a);
        float ty = y + (height - 8) / 2f;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(category.getName(), x + 14, ty, color);
        } else {
            mc.fontRendererObj.drawStringWithShadow(category.getName(), x + 12, y + 9, color);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return isHovered(mouseX, mouseY) && button == 0;
    }
}
