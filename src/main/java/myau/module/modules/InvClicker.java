package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiScreen;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

public class InvClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty triggerTicks = new IntProperty("start-delay", 2, 0, 20);
    public final BooleanProperty requireShift = new BooleanProperty("require-shift", false);
    public int ticks;

    public InvClicker() {
        super("InvClicker", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{triggerTicks.getValue().toString() + " ticks"};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && event.getType() == EventType.PRE) {
            if (mc.currentScreen instanceof GuiContainer) {
                GuiContainer screen = ((GuiContainer) mc.currentScreen);
                final int mouseX = Mouse.getEventX() * screen.width / mc.displayWidth;
                final int mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1;
                boolean shiftOk = !requireShift.getValue()
                        || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

                if (Mouse.isButtonDown(0) && shiftOk) {
                    ticks++;
                    if (ticks > triggerTicks.getValue()) {
                        ((IAccessorGuiScreen) screen).callMouseClicked(mouseX, mouseY, 0);
                    }
                } else {
                    ticks = 0;
                }
            }
        }
    }
}
