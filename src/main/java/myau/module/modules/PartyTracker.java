package myau.module.modules;

import myau.Myau;
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
    private static final long PARTY_WINDOW_MS = 50; // 短時間視窗，調整可偵測 "simultaneously"

    public PartyTracker() {
        super("PartyTracker", "Detects when multiple players join at the same time and announces party size.", Category.MISC, 0, false, false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;

        if (event.getPacket() instanceof S38PacketPlayerListItem) {
            S38PacketPlayerListItem packet = (S38PacketPlayerListItem) event.getPacket();
            if (packet.func_179767_a() == S38PacketPlayerListItem.Action.ADD_PLAYER) {  // ADD_PLAYER
                for (S38PacketPlayerListItem.AddPlayerData data : packet.func_179767_a()) {  // 注意：1.8.9 方法名
                    String name = data.getProfile().getName();
                    if (name != null && !recentJoins.contains(name)) {
                        recentJoins.add(name);
                        timer.reset();
                    }
                }
            }
        }

        // 檢查是否達到 party 門檻
        if (!recentJoins.isEmpty() && timer.hasTimeElapsed(PARTY_WINDOW_MS)) {
            int n = recentJoins.size();
            if (n >= 2) {  // n 個玩家同時加入
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
