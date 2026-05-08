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

        // 釋放 Jump 鍵（避免卡住）
        if (!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && cd.hasTimeElapsed(10)) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        if (mc.thePlayer.onGround && (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0)) {
            
            // === 與 Eagle 完全相同的邊緣偵測方法 ===
            double[] offset = MoveUtil.predictMovement();
            // 檢查往前移動後是否還能安全站立（無法站立 = 要跳）
            boolean shouldJump = !PlayerUtil.canMove(
                mc.thePlayer.motionX + offset[0], 
                mc.thePlayer.motionZ + offset[1]
            );

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
