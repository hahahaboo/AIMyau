package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.util.TimerUtil;

public class Parkour extends Module {

    public final BooleanProperty notOnSneaking = new BooleanProperty("not-on-sneaking", true);

    private final TimerUtil cd = new TimerUtil();

    public Parkour() {
        super("Parkour", "自動跳躍過空隙（Parkour Helper）", Category.PLAYER, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (e.getType() != EventType.PRE) return;
        
        // 【重要修正】加入模組啟用檢查，解決「關閉後仍運作」的問題
        if (!this.isEnabled()) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // 蹲下時不觸發 Parkour
        if (notOnSneaking.getValue() && PlayerUtil.isSneaking()) {
            return;
        }

        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && cd.hasTimeElapsed(10)) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        if (mc.thePlayer.onGround
                && isAboutToFallOffEdge()
                && (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0)) {
           
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            cd.reset();
        }
    }

    /**
     * 使用與 Eagle 相同的 util 進行邊緣偵測
     * 檢查往前移動是否會踏空（更貼近真實邊緣）
     */
    private boolean isAboutToFallOffEdge() {
        double[] offset = MoveUtil.predictMovement();
        // 檢查往前移動後是否還能安全站立（無碰撞 = 即將掉落）
        return !PlayerUtil.canMove(offset[0] * 0.3, offset[1] * 0.3, -0.5); // 稍微往前 + 下方檢查
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        cd.reset();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        // 可選：模組關閉時釋放 Jump 按鍵
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
}
