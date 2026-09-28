package myau.ui.impl.clickgui.element;

import net.minecraft.client.Minecraft;

public abstract class Element {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    public int x, y, width, height;

    public Element(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(int mouseX, int mouseY, float partialTicks, float alpha);

    public abstract boolean mouseClicked(int mouseX, int mouseY, int button);

    public void mouseReleased(int mouseX, int mouseY, int button) {}

    public void keyTyped(char typedChar, int keyCode) {}

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public int getHeight() {
        return height;
    }
}
