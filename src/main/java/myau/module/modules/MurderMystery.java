package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.events.LoadWorldEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;   // ← 新增
import myau.util.ChatUtil;
import myau.util.ItemUtil;
import myau.util.TeamUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;                    // ← 新增
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class MurderMystery extends Module {

    private final Set<String> detectedMurders = new HashSet<>();
    private final Set<String> detectedBows = new HashSet<>();     // ← 新增 bow 追蹤

    // 新增 Boolean 選項
    public final BooleanProperty bowDetect = new BooleanProperty("bow-detect", true);

    public MurderMystery() {
        super("MurderMystery", "Detect players holding murder weapons in Murder Mystery and announce them.", Category.MISC, 0, false, false);
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
            ItemStack heldItem = player.getHeldItem();
            if (heldItem == null) continue;

            // 原有 Murder Weapon 偵測
            if (!detectedMurders.contains(name) && ItemUtil.isMurderWeapon(heldItem)) {
                String itemName = heldItem.getDisplayName() != null && !heldItem.getDisplayName().isEmpty()
                        ? heldItem.getDisplayName().replace("§r", "")
                        : "weapon";

                ChatUtil.sendFormatted(String.format(
                    "%s%s: &l%s&r is &cMurder&r (%s&r)",
                    Myau.clientName,
                    this.getName(),
                    name,
                    itemName
                ));

                detectedMurders.add(name);
            }

            // 新增：Bow 偵測（由 bow-detect 控制）
            if (bowDetect.getValue() && !detectedBows.contains(name) && heldItem.getItem() instanceof ItemBow) {
                String bowName = heldItem.getDisplayName() != null && !heldItem.getDisplayName().isEmpty()
                        ? heldItem.getDisplayName().replace("§r", "")
                        : "bow";

                ChatUtil.sendFormatted(String.format(
                    "%s%s: &l%s&r has a &bBow&r (%s&r)",
                    Myau.clientName,
                    this.getName(),
                    name,
                    bowName
                ));

                detectedBows.add(name);
            }
        }
    }
    
    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        detectedMurders.clear();
        detectedBows.clear();
    }
    
    @Override
    public void onEnabled() {
        super.onEnabled();
        detectedMurders.clear();
        detectedBows.clear();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        detectedMurders.clear();
        detectedBows.clear();
    }
}
