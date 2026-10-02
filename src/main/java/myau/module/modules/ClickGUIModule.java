package myau.module.modules;

import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.ui.impl.clickgui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class ClickGUIModule extends Module {

    public BooleanProperty saveGuiState = new BooleanProperty("Save GUI State", true);

    public ClickGUIModule() {
        super("ClickGUI", "Material Design 3 based ClickGUI", Category.RENDER, Keyboard.KEY_RSHIFT, false, true);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (Minecraft.getMinecraft().theWorld == null) {
            this.setEnabled(false);
            return;
        }
        ClickGuiScreen gui = ClickGuiScreen.getInstance();
        if (gui != null) {
            Minecraft.getMinecraft().displayGuiScreen(gui);
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        Minecraft.getMinecraft().displayGuiScreen(null);
        if (Minecraft.getMinecraft().currentScreen == null) {
            Minecraft.getMinecraft().setIngameFocus();
        }
    }
}
