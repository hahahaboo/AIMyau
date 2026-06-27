package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.module.Category;
import myau.module.Module;
import myau.util.ChatUtil;
import myau.util.TimerUtil;
import net.minecraft.network.play.server.S38PacketPlayerListItem;

import java.util.ArrayList;
import java.util.List;

public class PartyTracker extends Module {
    private final TimerUtil timer = new TimerUtil();
    private final List<String> recentJoins = new ArrayList<>();
    private static final long PARTY_WINDOW_MS = 50;

    public PartyTracker() {
        super("PartyTracker", "Detects when multiple players join at the same time and announces party size.", Category.MISC, 0, false, true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;

        if (event.getPacket() instanceof S38PacketPlayerListItem) {
            S38PacketPlayerListItem packet = (S38PacketPlayerListItem) event.getPacket();
            
            try {
                // Reflection for 1.8.9 mapping compatibility
                java.lang.reflect.Method actionMethod = packet.getClass().getDeclaredMethod("func_179768_b");
                actionMethod.setAccessible(true);
                Object action = actionMethod.invoke(packet);
                
                if (action == S38PacketPlayerListItem.Action.ADD_PLAYER) {
                    java.lang.reflect.Method listMethod = packet.getClass().getDeclaredMethod("func_179767_a");
                    listMethod.setAccessible(true);
                    List<?> dataList = (List<?>) listMethod.invoke(packet);
                    
                    for (Object dataObj : dataList) {
                        if (dataObj instanceof S38PacketPlayerListItem.AddPlayerData) {
                            S38PacketPlayerListItem.AddPlayerData data = (S38PacketPlayerListItem.AddPlayerData) dataObj;
                            String name = data.getProfile().getName();
                            if (name != null && !recentJoins.contains(name)) {
                                recentJoins.add(name);
                                timer.reset();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallback
                if (packet.toString().toLowerCase().contains("add_player")) {
                    ChatUtil.sendFormatted("&7[&dPartyTracker&7] &ePlayer join detected");
                }
            }
        }

        if (!recentJoins.isEmpty() && timer.hasTimeElapsed(PARTY_WINDOW_MS)) {
            int n = recentJoins.size();
            if (n >= 2) {
                ChatUtil.sendFormatted("&7[&dPartyTracker&7] &fParty of &d" + n + " &fjoined");
            }
            recentJoins.clear();
        }
    }

    @Override
    public void onEnabled() {
        recentJoins.clear();
        timer.reset();
    }

    @Override
    public void onDisabled() {
        recentJoins.clear();
    }
}
