package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.mixin.IAccessorEntityLivingBase;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

public class Sprint extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty foxFix = new BooleanProperty("fov-fix", true);

    private boolean wasSprinting = false;

    public Sprint() {
        super("Sprint", "Automatically sprints for you.", Category.MOVEMENT, 0, true, true);
    }

    // 判斷是否正在使用 Grim InvWalk（專門針對 Grim mode）
    private boolean isGrimInvWalkActive() {
        Module invWalk = Myau.moduleManager.getModule("InvWalk");
        if (invWalk == null || !invWalk.isEnabled()) return false;

        // GRIM mode 的 index 是 3（從你的 InvWalk mode 設定來看）
        return ((myau.module.modules.InvWalk) invWalk).mode.getValue() == 3 &&
               mc.currentScreen instanceof GuiContainer &&
               !(mc.currentScreen instanceof GuiContainerCreative);
    }

    public boolean shouldApplyFovFix(IAttributeInstance attribute) {
        if (!this.foxFix.getValue()) {
            return false;
        } else {
            AttributeModifier attributeModifier = ((IAccessorEntityLivingBase) mc.thePlayer).getSprintingSpeedBoostModifier();
            return attribute.getModifier(attributeModifier.getID()) == null && this.wasSprinting;
        }
    }

    public boolean shouldKeepFov(boolean boolean2) {
        return this.foxFix.getValue() && !boolean2 && this.wasSprinting;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;

        switch (event.getType()) {
            case PRE:
                // 原本強制按 sprint key
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);

                // === Grim mode 專用 resprint 加強（只在 Grim InvWalk 暫停移動後生效）===
                if (isGrimInvWalkActive() && mc.thePlayer != null) {
                    // 如果 sprint key 還按著，但目前沒 sprint 狀態，就強制恢復（確保 Grim 暫停後能立刻接上）
                    if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()) &&
                        !mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(true);
                    }
                }
                break;

            case POST:
                this.wasSprinting = mc.thePlayer.isSprinting();
                break;
        }
    }

    @Override
    public void onDisabled() {
        this.wasSprinting = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
    }
}
