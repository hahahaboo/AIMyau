package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.ItemUtil;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

public class AutoWeapon extends Module {
    public final BooleanProperty onlyWhenHoldingDown = new BooleanProperty("Only when holding lmb", true);
    public final BooleanProperty goBackToPrevSlot = new BooleanProperty("Revert to old slot", true);

    private boolean onWeapon;
    private int prevSlot;

    public AutoWeapon() {
        super("AutoWeapon", "Automatically switch to the best damage weapon when looking at an entity", Category.COMBAT, 0, false, false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;   // ←←← 新增這一行（修復 bug 的關鍵）

        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null)
            return;

        if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null
                || (onlyWhenHoldingDown.getValue() && !Mouse.isButtonDown(0))) {
            if (onWeapon) {
                onWeapon = false;
                if (goBackToPrevSlot.getValue()) {
                    mc.thePlayer.inventory.currentItem = prevSlot;
                }
            }
        } else {
            if (onlyWhenHoldingDown.getValue()) {
                if (!Mouse.isButtonDown(0))
                    return;
            }

            if (!onWeapon) {
                prevSlot = mc.thePlayer.inventory.currentItem;
                onWeapon = true;

                int maxDamageSlot = getMaxDamageSlot();

                if (maxDamageSlot >= 0 && getSlotDamage(maxDamageSlot) > getSlotDamage(mc.thePlayer.inventory.currentItem)) {
                    mc.thePlayer.inventory.currentItem = maxDamageSlot;
                }
            }
        }
    }

    private int getMaxDamageSlot() {
        int index = -1;
        double damage = -1.0;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
            if (itemInSlot == null)
                continue;

            double d = ItemUtil.getAttackBonus(itemInSlot);
            if (d > damage) {
                damage = d;
                index = slot;
            }
        }
        return index;
    }

    private double getSlotDamage(int slot) {
        ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
        if (itemInSlot == null)
            return -1.0;
        return ItemUtil.getAttackBonus(itemInSlot);
    }
}
