package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.util.ChatUtil;
import myau.util.TeamUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.HashSet;
import java.util.Set;

public class MurderMystery extends Module {

    private final Set<String> detectedMurders = new HashSet<>();

    public MurderMystery() {
        super("MurderMystery", "Detect players holding swords in Murder Mystery and announce them.", Category.MISC, 0, false, false);
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
            if (heldItem != null && heldItem.getItem() instanceof ItemSword) {
                String swordName = heldItem.getDisplayName() != null && !heldItem.getDisplayName().isEmpty() 
                        ? heldItem.getDisplayName() 
                        : "sword";

                ChatUtil.sendFormatted(String.format("&c%s is Murder (%s)&r", name, swordName));
                detectedMurders.add(name);
            }
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        detectedMurders.clear(); // 每次開啟模組重置偵測紀錄
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        detectedMurders.clear();
    }
}
