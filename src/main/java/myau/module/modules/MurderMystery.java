// === 修改後完整內容（重點變更處已標註）===
package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.util.ChatUtil;
import myau.util.ItemUtil;          // <-- 新增 import
import myau.util.TeamUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class MurderMystery extends Module {

    private final Set<String> detectedMurders = new HashSet<>();

    public MurderMystery() {
        super("MurderMystery", "Detect players holding murder weapons in Murder Mystery and announce them.", Category.MISC, 0, false, false); // <-- 更新描述
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled() || mc.theWorld == null) {
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || !TeamUtil.isEntityLoaded(player)) {
                continue;
            }

            String name = player.getName();
            if (detectedMurders.contains(name)) {
                continue;
            }

            ItemStack heldItem = player.getHeldItem();
            if (heldItem != null && ItemUtil.isMurderWeapon(heldItem)) {  // <-- 核心更改：使用新 helper
                String itemName = heldItem.getDisplayName() != null && !heldItem.getDisplayName().isEmpty() 
                        ? heldItem.getDisplayName().replace("§r", "") 
                        : "weapon";

                ChatUtil.sendFormatted(String.format(
                    "%s%s: %s&r is &cMurder&r (%s)&r", 
                    Myau.clientName, 
                    this.getName(), 
                    name, 
                    itemName
                ));

                detectedMurders.add(name);
            }
        }
    }

    // onEnabled / onDisabled 保持不變
    @Override
    public void onEnabled() {
        super.onEnabled();
        detectedMurders.clear();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        detectedMurders.clear();
    }
}
