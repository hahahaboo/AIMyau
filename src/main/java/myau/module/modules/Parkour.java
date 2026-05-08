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
        
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (notOnSneaking.getValue() && PlayerUtil.isSneaking()) {
            return;
        }

        // 釋放 Jump 鍵
        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && cd.hasTimeElapsed(50)) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        if (mc.thePlayer.onGround && MoveUtil.isMoving()) {
            
            // === 與 Eagle 完全相同的預測方式，但改用更適合 Parkour 的檢查 ===
            double[] offset = MoveUtil.predictMovement();
            
            // 關鍵修正：往下檢查 -0.5 ~ -1.0 更準確判斷是否會掉落
            boolean canLandSafely = PlayerUtil.canMove(
                offset[0] * 1.05,   // 略微放大前進距離
                offset[1] * 1.05, 
                -0.6                // 往下檢查高度（最重要參數）
            );

            boolean shouldJump = !canLandSafely;

            if (shouldJump) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                cd.reset();
            }
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        cd.reset();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
}
