package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Hold-Key（按住啟用）
    public final IntProperty holdKey = new IntProperty("Hold-Key", 0, 0, 223);

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    private boolean toggled = false;
    private boolean wasKeyPressed = false;   // 用來偵測 Toggle Key 的按下邊緣

    public FreeLook() {
        super("FreeLook", "Look around in 3rd person without changing your direction", Category.RENDER, 0, false, false);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) return;

        previousPerspective = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 1;

        freeYaw = prevFreeYaw = mc.thePlayer.rotationYaw;
        freePitch = prevFreePitch = mc.thePlayer.rotationPitch;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.thePlayer == null || event.getType() != EventType.PRE) return;

        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;

        int toggleKeyCode = getKey();

        // === Toggle 模式：使用模組本身的 Keybind ===
        if (toggleKeyCode != 0) {
            boolean isCurrentlyPressed = KeyBindUtil.isKeyDown(toggleKeyCode);
            
            if (isCurrentlyPressed && !wasKeyPressed) {
                toggled = !toggled;
            }
            wasKeyPressed = isCurrentlyPressed;
        }

        // === Hold 模式 ===
        boolean holdActive = holdKey.getValue() != 0 && KeyBindUtil.isKeyDown(holdKey.getValue());

        boolean shouldBeEnabled = toggled || holdActive;

        // 只有狀態改變時才切換
        if (isEnabled() != shouldBeEnabled) {
            setEnabled(shouldBeEnabled);
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
        toggled = false;
        wasKeyPressed = false;
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        if (this.freePitch > 90.0F) this.freePitch = 90.0F;
        if (this.freePitch < -90.0F) this.freePitch = -90.0F;
    }

    @Override
    public String[] getSuffix() {
        String holdStr = holdKey.getValue() == 0 ? "" : "H:" + KeyBindUtil.getKeyName(holdKey.getValue());
        String toggleStr = getKey() == 0 ? "" : "T:" + KeyBindUtil.getKeyName(getKey());

        if (!holdStr.isEmpty() && !toggleStr.isEmpty()) {
            return new String[]{holdStr + " | " + toggleStr};
        } else if (!holdStr.isEmpty()) {
            return new String[]{holdStr};
        } else if (!toggleStr.isEmpty()) {
            return new String[]{toggleStr};
        }
        return new String[]{"None"};
    }
}
