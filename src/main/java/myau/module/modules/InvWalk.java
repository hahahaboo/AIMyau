package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.KeyBinding;

public class InvWalk extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0,
            new String[]{"VANILLA", "UNSPRINT"});

    private boolean keysPressed = false;

    public InvWalk() {
        super("InvWalk", "Allows you to walk while in inventories.", Category.MOVEMENT, 0, false, false);
    }

    public void pressMovementKeys() {

        KeyBinding[] movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump
        };

        for (KeyBinding keyBinding : movementKeys) {
            KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
        }

        boolean unsprintMode = this.mode.getValue() == 1;

        if (unsprintMode && mc.currentScreen instanceof GuiContainer) {

            // 強制取消 sprint
            mc.thePlayer.setSprinting(false);
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);

        } else {

            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());

            if (Myau.moduleManager.modules.get(Sprint.class).isEnabled()) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            }
        }

        this.keysPressed = true;
    }

    public boolean canInvWalk() {

        if (!(mc.currentScreen instanceof GuiContainer)) {
            return false;
        }

        if (mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }

        switch (this.mode.getValue()) {
            case 1: // UNSPRINT
                return true;

            default: // VANILLA
                return true;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {

        if (this.isEnabled() && event.getType() == EventType.PRE) {

            if (this.canInvWalk()) {

                this.pressMovementKeys();

                // 確保 GUI 中不 sprint
                if (this.mode.getValue() == 1 && mc.currentScreen instanceof GuiContainer) {
                    mc.thePlayer.setSprinting(false);
                }

            } else {

                if (this.keysPressed) {

                    if (mc.currentScreen != null) {
                        KeyBinding.unPressAllKeys();
                    }

                    this.keysPressed = false;
                }
            }
        }
    }

    @Override
    public void onDisabled() {

        if (this.keysPressed) {

            if (mc.currentScreen != null) {
                KeyBinding.unPressAllKeys();
            }

            this.keysPressed = false;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{
                CaseFormat.UPPER_UNDERSCORE.to(
                        CaseFormat.UPPER_CAMEL,
                        this.mode.getModeString())
        };
    }
}
